package polyomino.dotfiles.calendar

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.ThemeEngine

object CalendarPopup:
  def run(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    // Toggle behavior: check PID file first, fallback to precise python runner process
    val pidFile = ctx.shareDir / "calendar.pid"
    if os.exists(pidFile) then
      val savedPid = os.read(pidFile).trim
      if savedPid.nonEmpty && savedPid.forall(_.isDigit) then
        try
          val check = os.proc("kill", "-0", savedPid).call(check = false)
          if check.exitCode == 0 then
            os.proc("kill", savedPid).call(check = false)
            os.remove(pidFile)
            return Right(())
          else
            os.remove(pidFile)
        catch case _: Exception => ()

    val myPid = ProcessHandle.current().pid()
    try
      val checkRes = os.proc("pgrep", "-f", "python3.*polyomino-calendar-runner").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty).map(_.toLong).filter(_ != myPid)
        if pids.nonEmpty then
          for pid <- pids do
            try os.proc("kill", pid.toString).call(check = false) catch case _: Exception => ()
          if os.exists(pidFile) then os.remove(pidFile)
          return Right(())
    catch
      case _: Exception => ()

    // Ensure helper script is deployed in ~/.local/share/polyomino/
    val runnerScript = ctx.shareDir / "polyomino-calendar-runner.py"
    os.makeDir.all(ctx.shareDir)
    os.write.over(runnerScript, PythonRunnerScript)
    try os.proc("chmod", "+x", runnerScript.toString).call(check = false) catch case _: Exception => ()

    val palette = ThemeEngine.getActivePalette(ctx)
    val waybarTheme = ctx.configDir / "waybar" / "theme.css"
    val themePath = if os.exists(waybarTheme) then waybarTheme.toString else ""
    val targetOutput = args.sliding(2).collectFirst { case Seq("--output", name) => name }.getOrElse("")

    try
      val proc = os.proc(
        "python3", runnerScript.toString,
        "--base", palette.base,
        "--accent", palette.accent,
        "--text", palette.text,
        "--mantle", palette.mantle,
        "--red", palette.red,
        "--theme-file", themePath,
        "--target-output", targetOutput
      ).spawn(stdout = os.Inherit, stderr = os.Inherit)
      try os.write.over(pidFile, proc.wrapped.pid().toString) catch case _: Exception => ()
      Right(())
    catch
      case e: Exception => Left(CommandError(s"Calendar popup launch failed: ${e.getMessage}"))

  val PythonRunnerScript: String =
    """#!/usr/bin/env python3
      |import sys
      |import os
      |import signal
      |import datetime
      |import calendar
      |import argparse
      |import json
      |import subprocess
      |import gi
      |
      |gi.require_version("Gtk", "3.0")
      |gi.require_version("GtkLayerShell", "0.1")
      |from gi.repository import Gtk, Gdk, GtkLayerShell, GLib, Pango
      |
      |DEBUG_LOG = os.path.expanduser("~/.local/share/polyomino/calendar-debug.log")
      |
      |def dlog(msg):
      |    try:
      |        if os.path.exists(DEBUG_LOG) and os.path.getsize(DEBUG_LOG) > 256000:
      |            try:
      |                os.remove(DEBUG_LOG)
      |            except Exception:
      |                pass
      |        with open(DEBUG_LOG, "a") as f:
      |            f.write(f"[{datetime.datetime.now().isoformat()}] {msg}\\n")
      |    except Exception:
      |        pass
      |
      |def get_outputs():
      |    try:
      |        out = subprocess.run(["swaymsg", "-t", "get_outputs"], capture_output=True, timeout=2, check=True).stdout
      |        return json.loads(out)
      |    except Exception as e:
      |        dlog(f"get_outputs failed: {e!r}")
      |        return []
      |
      |def find_output_rect_by_name(name, source):
      |    # xdotool/XWayland pointer queries are stale for native-Wayland
      |    # surfaces like waybar itself and must not be used to find "which
      |    # screen was clicked". WAYBAR_OUTPUT_NAME is also unreliable: it is
      |    # not set for built-in modules' on-click exec on this waybar build.
      |    # So the output name is instead passed explicitly via --target-output
      |    # from a per-monitor waybar bar config (see find_target_output_rect).
      |    if not name:
      |        dlog(f"{source}: no output name given")
      |        return None
      |    outputs = get_outputs()
      |    dlog(f"{source}: name={name} outputs={[(o.get('name'), o.get('rect')) for o in outputs]}")
      |    for o in outputs:
      |        if o.get("name") == name:
      |            r = o.get("rect")
      |            dlog(f"{source}: matched output {name} rect={r}")
      |            return r
      |    dlog(f"{source}: no output named {name} found")
      |    return None
      |
      |def find_target_output_rect(target_output_arg):
      |    r = find_output_rect_by_name(target_output_arg, "target-output-arg")
      |    if r is not None:
      |        return r
      |    return find_output_rect_by_name(os.environ.get("WAYBAR_OUTPUT_NAME"), "waybar-env")
      |
      |def find_focused_output_rect():
      |    outputs = get_outputs()
      |    for o in outputs:
      |        if o.get("focused"):
      |            return o.get("rect")
      |    for o in outputs:
      |        if o.get("active"):
      |            return o.get("rect")
      |    return None
      |
      |def find_gdk_monitor(output_rect):
      |    if not output_rect:
      |        dlog("find_gdk_monitor: no output_rect given")
      |        return None
      |    display = Gdk.Display.get_default()
      |    if display is None:
      |        dlog("find_gdk_monitor: Gdk.Display.get_default() is None")
      |        return None
      |    geos = []
      |    for i in range(display.get_n_monitors()):
      |        monitor = display.get_monitor(i)
      |        geo = monitor.get_geometry()
      |        geos.append((i, geo.x, geo.y, geo.width, geo.height))
      |        if geo.x == output_rect.get("x") and geo.y == output_rect.get("y"):
      |            dlog(f"find_gdk_monitor: matched gdk monitor {i} geo=({geo.x},{geo.y}) to output_rect={output_rect}")
      |            return monitor
      |    dlog(f"find_gdk_monitor: no match for output_rect={output_rect}, gdk monitors={geos}")
      |    return None
      |
      |class CustomCalendar(Gtk.Box):
      |    def __init__(self, theme_colors):
      |        super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=8)
      |        self.theme_colors = theme_colors
      |        self.now = datetime.datetime.now()
      |        self.view_year = self.now.year
      |        self.view_month = self.now.month
      |        self.selected_date = (self.now.year, self.now.month, self.now.day)
      |        
      |        # Month navigation header
      |        nav_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
      |        nav_box.set_name("cal-nav")
      |        
      |        self.prev_btn = Gtk.Button(label="<")
      |        self.prev_btn.set_name("nav-btn")
      |        self.prev_btn.connect("clicked", self.prev_month)
      |        nav_box.pack_start(self.prev_btn, False, False, 0)
      |        
      |        self.month_label = Gtk.Label()
      |        self.month_label.set_name("month-header-label")
      |        self.month_label.set_xalign(0.5)
      |        nav_box.pack_start(self.month_label, True, True, 0)
      |        
      |        self.next_btn = Gtk.Button(label=">")
      |        self.next_btn.set_name("nav-btn")
      |        self.next_btn.connect("clicked", self.next_month)
      |        nav_box.pack_end(self.next_btn, False, False, 0)
      |        
      |        self.pack_start(nav_box, False, False, 0)
      |        
      |        # Fixed Calendar Grid (7 columns x 6 rows)
      |        self.grid = Gtk.Grid()
      |        self.grid.set_name("cal-grid")
      |        self.grid.set_column_homogeneous(True)
      |        self.grid.set_row_homogeneous(True)
      |        self.grid.set_column_spacing(4)
      |        self.grid.set_row_spacing(4)
      |        self.pack_start(self.grid, True, True, 0)
      |        
      |        weekdays = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]
      |        for col, wd in enumerate(weekdays):
      |            lbl = Gtk.Label()
      |            lbl.set_markup(f"<span weight='bold' size='90%'>{wd}</span>")
      |            lbl.set_name("weekday-header")
      |            lbl.set_xalign(0.5)
      |            lbl.set_yalign(0.5)
      |            lbl.set_size_request(34, 26)
      |            self.grid.attach(lbl, col, 0, 1, 1)
      |            
      |        self.day_buttons = []
      |        for row in range(6):
      |            row_btns = []
      |            for col in range(7):
      |                btn = Gtk.Button()
      |                btn.set_relief(Gtk.ReliefStyle.NONE)
      |                btn.set_size_request(34, 30)
      |                lbl = Gtk.Label(label="")
      |                lbl.set_xalign(0.5)
      |                lbl.set_yalign(0.5)
      |                btn.add(lbl)
      |                btn.connect("clicked", self.on_day_clicked)
      |                self.grid.attach(btn, col, row + 1, 1, 1)
      |                row_btns.append(btn)
      |            self.day_buttons.append(row_btns)
      |        
      |        # Dot matrix divider
      |        dot_divider = Gtk.Label()
      |        dot_divider.set_markup("<span size='80%' alpha='35%'>·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·</span>")
      |        dot_divider.set_xalign(0.5)
      |        self.pack_start(dot_divider, False, False, 0)
      |
      |        # Telemetry footer: date/relative status line + segmented instrument pod
      |        self.diff_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
      |        self.diff_box.set_name("cal-diff")
      |
      |        self.diff_title = Gtk.Label()
      |        self.diff_title.set_name("cal-diff-title")
      |        self.diff_title.set_xalign(0.5)
      |        self.diff_title.set_ellipsize(Pango.EllipsizeMode.END)
      |        self.diff_box.pack_start(self.diff_title, False, False, 0)
      |
      |        self.footer_pod = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=0)
      |        self.footer_pod.set_name("cal-footer")
      |        self.footer_pod.set_halign(Gtk.Align.CENTER)
      |
      |        self.footer_seg1 = Gtk.Label()
      |        self.footer_seg2 = Gtk.Label()
      |        self.footer_seg3 = Gtk.Label()
      |        for i, seg in enumerate([self.footer_seg1, self.footer_seg2, self.footer_seg3]):
      |            seg.set_name("footer-segment")
      |            seg.set_xalign(0.5)
      |            if i == 2:
      |                seg.get_style_context().add_class("last-segment")
      |            self.footer_pod.pack_start(seg, False, False, 0)
      |        self.diff_box.pack_start(self.footer_pod, False, False, 0)
      |
      |        self.pack_start(self.diff_box, False, False, 0)
      |        
      |        self.render_calendar()
      |
      |    def on_day_clicked(self, btn):
      |        if hasattr(btn, "_day") and btn._day > 0:
      |            self.selected_date = (self.view_year, self.view_month, btn._day)
      |            self.render_calendar()
      |
      |    def calculate_diff(self, d_today, d_sel):
      |        sel_fmt = d_sel.strftime("%a, %d %b %Y")
      |        delta = (d_sel - d_today).days
      |        if delta == 0:
      |            rel_str = "Today"
      |            day_of_year = d_sel.timetuple().tm_yday
      |            is_leap = calendar.isleap(d_sel.year)
      |            total_days = 366 if is_leap else 365
      |            week_num = d_sel.isocalendar()[1]
      |            quarter = (d_sel.month - 1) // 3 + 1
      |            self.diff_title.set_markup(f"<span weight='bold'>{sel_fmt}</span> <span alpha='60%'>({rel_str})</span>")
      |            self.footer_seg1.set_markup(f"[ DAY {day_of_year}/{total_days}")
      |            self.footer_seg2.set_markup(f"WEEK {week_num}")
      |            self.footer_seg3.set_markup(f"Q{quarter} ]")
      |            return
      |
      |        abs_days = abs(delta)
      |        is_future = delta > 0
      |        rel_str = f"in {abs_days}d" if is_future else f"{abs_days}d ago"
      |        
      |        # Weeks & remaining days
      |        weeks = abs_days // 7
      |        rem_w_days = abs_days % 7
      |        if weeks > 0 and rem_w_days > 0:
      |            weeks_str = f"{weeks}w {rem_w_days}d"
      |        elif weeks > 0:
      |            weeks_str = f"{weeks}w"
      |        else:
      |            weeks_str = f"{rem_w_days}d"
      |
      |        # Months & remaining days
      |        start_d, end_d = (d_today, d_sel) if is_future else (d_sel, d_today)
      |        months = (end_d.year - start_d.year) * 12 + (end_d.month - start_d.month)
      |        if end_d.day < start_d.day:
      |            months -= 1
      |        
      |        int_m = start_d.month + months
      |        int_y = start_d.year + (int_m - 1) // 12
      |        int_m = ((int_m - 1) % 12) + 1
      |        max_d = calendar.monthrange(int_y, int_m)[1]
      |        anchor = datetime.date(int_y, int_m, min(start_d.day, max_d))
      |        m_rem_days = (end_d - anchor).days
      |        
      |        if months > 0 and m_rem_days > 0:
      |            months_str = f"{months}mo {m_rem_days}d"
      |        elif months > 0:
      |            months_str = f"{months}mo"
      |        else:
      |            months_str = f"{abs_days}d"
      |
      |        days_str = f"{abs_days}d"
      |        self.diff_title.set_markup(f"<span weight='bold'>{sel_fmt}</span> <span alpha='60%'>({rel_str})</span>")
      |        self.footer_seg1.set_markup(f"[ {days_str}")
      |        self.footer_seg2.set_markup(f"{weeks_str}")
      |        self.footer_seg3.set_markup(f"{months_str} ]")
      |
      |    def prev_month(self, btn):
      |        if self.view_month == 1:
      |            self.view_month = 12
      |            self.view_year -= 1
      |        else:
      |            self.view_month -= 1
      |        self.render_calendar()
      |
      |    def next_month(self, btn):
      |        if self.view_month == 12:
      |            self.view_month = 1
      |            self.view_year += 1
      |        else:
      |            self.view_month += 1
      |        self.render_calendar()
      |
      |    def render_calendar(self):
      |        month_name = datetime.date(self.view_year, self.view_month, 1).strftime("%B %Y")
      |        self.month_label.set_markup(f"<span weight='bold' size='115%'>{month_name}</span>")
      |        
      |        d_today = datetime.date(self.now.year, self.now.month, self.now.day)
      |        d_sel = datetime.date(self.selected_date[0], self.selected_date[1], self.selected_date[2])
      |        r_start = min(d_today, d_sel)
      |        r_end = max(d_today, d_sel)
      |        
      |        cal_matrix = calendar.monthcalendar(self.view_year, self.view_month)
      |        while len(cal_matrix) < 6:
      |            cal_matrix.append([0]*7)
      |            
      |        for row in range(6):
      |            for col in range(7):
      |                day = cal_matrix[row][col]
      |                btn = self.day_buttons[row][col]
      |                lbl = btn.get_child()
      |                if day == 0:
      |                    btn._day = 0
      |                    lbl.set_text("")
      |                    btn.set_sensitive(False)
      |                    btn.set_name("day-cube-empty")
      |                else:
      |                    btn._day = day
      |                    lbl.set_text(str(day))
      |                    btn.set_sensitive(True)
      |                    cur_d = datetime.date(self.view_year, self.view_month, day)
      |                    if cur_d == d_today:
      |                        btn.set_name("day-cube-today")
      |                    elif cur_d == d_sel:
      |                        btn.set_name("day-cube-selected")
      |                    elif r_start < cur_d < r_end:
      |                        btn.set_name("day-cube-range")
      |                    else:
      |                        btn.set_name("day-cube")
      |                    
      |        self.calculate_diff(d_today, d_sel)
      |        self.grid.show_all()
      |        self.diff_box.show_all()
      |
      |def main():
      |    parser = argparse.ArgumentParser()
      |    parser.add_argument("--base", default="#0F1117")
      |    parser.add_argument("--accent", default="#EBB434")
      |    parser.add_argument("--text", default="#F8FAFC")
      |    parser.add_argument("--mantle", default="#191C24")
      |    parser.add_argument("--red", default="#EF4444")
      |    parser.add_argument("--theme-file", default="")
      |    parser.add_argument("--target-output", default="")
      |    args = parser.parse_args()
      |
      |    base_color = args.base
      |    accent_color = args.accent
      |    text_color = args.text
      |    mantle_color = args.mantle
      |    red_color = args.red
      |
      |    if args.theme_file and os.path.exists(args.theme_file):
      |        try:
      |            with open(args.theme_file) as f:
      |                for line in f:
      |                    if "@define-color base" in line:
      |                        base_color = line.split()[-1].strip("; ")
      |                    elif "@define-color accent" in line:
      |                        accent_color = line.split()[-1].strip("; ")
      |                    elif "@define-color text" in line:
      |                        text_color = line.split()[-1].strip("; ")
      |                    elif "@define-color mantle" in line:
      |                        mantle_color = line.split()[-1].strip("; ")
      |                    elif "@define-color red" in line:
      |                        red_color = line.split()[-1].strip("; ")
      |        except Exception:
      |            pass
      |
      |    # Fixed "arcade CAD terminal" palette. Deliberately independent of the
      |    # active desktop theme so the calendar keeps the same violet/gold
      |    # instrument-panel look no matter which palette is active elsewhere.
      |    cad_bg = "#0d1117"
      |    cad_violet = "#8b5cf6"
      |    cad_border_muted = "#30363d"
      |    cad_text_muted = "#8b949e"
      |    cad_gold = "#f59e0b"
      |    cad_today_bg = "rgba(139, 92, 246, 0.18)"
      |    cad_range_bg = "rgba(139, 92, 246, 0.25)"
      |    cad_footer_bg = "#161b22"
      |    cad_footer_divider = "#21262d"
      |
      |    win = Gtk.Window()
      |    win.set_name("calendar-window")
      |    win.set_title("Polyomino Calendar")
      |    win.set_resizable(False)
      |    win.set_size_request(340, 430)
      |    # Without an RGBA visual the GTK toplevel paints an opaque (theme-default,
      |    # usually white) background, which shows through at the four corners left
      |    # bare by the card's rounded border. Give the window a real alpha channel
      |    # and let CSS paint it fully transparent so only the card is visible.
      |    win.set_app_paintable(True)
      |    _screen = win.get_screen()
      |    _rgba_visual = _screen.get_rgba_visual() if _screen is not None else None
      |    if _rgba_visual is not None:
      |        win.set_visual(_rgba_visual)
      |
      |    GtkLayerShell.init_for_window(win)
      |    GtkLayerShell.set_layer(win, GtkLayerShell.Layer.TOP)
      |    GtkLayerShell.set_namespace(win, "polyomino-calendar-popup")
      |    target_rect = find_target_output_rect(args.target_output)
      |    dlog(f"target_rect = {target_rect} (--target-output={args.target_output!r})")
      |    if target_rect is None:
      |        target_rect = find_focused_output_rect()
      |        dlog(f"fell back to focused output rect = {target_rect}")
      |    gdk_monitor = find_gdk_monitor(target_rect)
      |    dlog(f"final gdk_monitor = {gdk_monitor}")
      |    if gdk_monitor is not None:
      |        GtkLayerShell.set_monitor(win, gdk_monitor)
      |    # Anchor to the top-right corner, under the clock/notification cluster at
      |    # the right end of Waybar's modules-right. The TOP margin is measured from
      |    # the bottom of Waybar's exclusive zone (its 8px margin + 32px height), so a
      |    # small value here is the actual gap below the bar; the RIGHT margin lines
      |    # the popup's right edge up with the instrument strip's own right margin.
      |    GtkLayerShell.set_anchor(win, GtkLayerShell.Edge.TOP, True)
      |    GtkLayerShell.set_anchor(win, GtkLayerShell.Edge.RIGHT, True)
      |    GtkLayerShell.set_anchor(win, GtkLayerShell.Edge.LEFT, False)
      |    GtkLayerShell.set_anchor(win, GtkLayerShell.Edge.BOTTOM, False)
      |    GtkLayerShell.set_margin(win, GtkLayerShell.Edge.TOP, 6)
      |    GtkLayerShell.set_margin(win, GtkLayerShell.Edge.RIGHT, 8)
      |    GtkLayerShell.set_keyboard_mode(win, GtkLayerShell.KeyboardMode.ON_DEMAND)
      |
      |    popup_card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
      |    popup_card.set_name("calendar-card")
      |    popup_card.set_size_request(340, 430)
      |    win.add(popup_card)
      |
      |    header_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
      |    
      |    header_label = Gtk.Label()
      |    header_label.set_markup("<span font_family='JetBrainsMono Nerd Font, monospace' weight='bold' letter_spacing='1024'>POLYOMINO // CALENDAR</span>")
      |    header_label.set_name("calendar-header")
      |    header_label.set_xalign(0.0)
      |    header_box.pack_start(header_label, True, True, 0)
      |
      |    close_btn = Gtk.Button(label="[×]")
      |    close_btn.set_name("calendar-close-btn")
      |    close_btn.set_relief(Gtk.ReliefStyle.NONE)
      |    close_btn.connect("clicked", lambda b: Gtk.main_quit())
      |    header_box.pack_end(close_btn, False, False, 0)
      |    popup_card.pack_start(header_box, False, False, 0)
      |
      |    custom_cal = CustomCalendar({"base": base_color, "accent": accent_color})
      |    popup_card.pack_start(custom_cal, True, True, 0)
      |
      |    def on_key_press(widget, event):
      |        if event.keyval == Gdk.KEY_Escape:
      |            Gtk.main_quit()
      |            return True
      |        return False
      |    win.connect("key-press-event", on_key_press)
      |
      |    def on_focus_out(widget, event):
      |        toplevel = win.get_toplevel()
      |        if not toplevel.has_toplevel_focus():
      |            GLib.timeout_add(150, lambda: Gtk.main_quit() if not win.has_toplevel_focus() else None)
      |        return False
      |    win.connect("focus-out-event", on_focus_out)
      |
      |    css_provider = Gtk.CssProvider()
      |    custom_css = f'''
      |    window#calendar-window,
      |    window#calendar-window.background,
      |    window#calendar-window decoration {{
      |        background-color: transparent;
      |        background-image: none;
      |        box-shadow: none;
      |        border: none;
      |    }}
      |    #calendar-card {{
      |        background-color: {cad_bg};
      |        border: 1px solid {cad_violet};
      |        border-radius: 0px;
      |        padding: 12px 14px;
      |        min-width: 360px;
      |        min-height: 430px;
      |        box-shadow: none;
      |    }}
      |    #calendar-header {{
      |        color: {cad_violet};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-size: 13px;
      |        font-weight: bold;
      |    }}
      |    button,
      |    button:backdrop,
      |    button:focus,
      |    button:active {{
      |        background-image: none;
      |        box-shadow: none;
      |        outline: none;
      |    }}
      |    #calendar-close-btn,
      |    button#calendar-close-btn {{
      |        background: transparent;
      |        background-color: transparent;
      |        background-image: none;
      |        box-shadow: none;
      |        color: {cad_text_muted};
      |        font-size: 12px;
      |        font-weight: bold;
      |        padding: 2px 6px;
      |        border-radius: 0px;
      |        border: none;
      |    }}
      |    #calendar-close-btn:hover,
      |    button#calendar-close-btn:hover {{
      |        background: {red_color};
      |        background-color: {red_color};
      |        color: {cad_bg};
      |    }}
      |    #cal-nav {{
      |        background-color: {cad_bg};
      |        border: 1px solid {cad_border_muted};
      |        border-radius: 0px;
      |        padding: 4px 6px;
      |    }}
      |    #month-header-label {{
      |        color: {cad_violet};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-weight: bold;
      |    }}
      |    button#nav-btn,
      |    button#nav-btn:backdrop,
      |    button#nav-btn:focus,
      |    #nav-btn {{
      |        background: transparent;
      |        background-color: transparent;
      |        background-image: none;
      |        box-shadow: none;
      |        border: none;
      |        border-radius: 0px;
      |        padding: 0 8px;
      |    }}
      |    button#nav-btn label,
      |    #nav-btn label {{
      |        color: {cad_violet};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-size: 16px;
      |        font-weight: bold;
      |    }}
      |    button#nav-btn:hover,
      |    #nav-btn:hover {{
      |        background: {cad_violet};
      |        background-color: {cad_violet};
      |        background-image: none;
      |        box-shadow: none;
      |    }}
      |    button#nav-btn:hover label,
      |    #nav-btn:hover label {{
      |        color: {cad_bg};
      |    }}
      |    #cal-grid {{
      |        background-color: {cad_bg};
      |        border: 1px solid {cad_border_muted};
      |        border-radius: 0px;
      |        padding: 8px;
      |    }}
      |    #weekday-header {{
      |        color: {cad_text_muted};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-weight: bold;
      |        font-size: 12px;
      |    }}
      |    #day-cube,
      |    #day-cube-today,
      |    #day-cube-selected,
      |    #day-cube-range,
      |    #day-cube-empty,
      |    button#day-cube,
      |    button#day-cube-today,
      |    button#day-cube-selected,
      |    button#day-cube-range,
      |    button#day-cube-empty {{
      |        background-image: none;
      |        box-shadow: none;
      |        border-radius: 0px;
      |        padding: 0;
      |        margin: 0;
      |        border: none;
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-size: 13px;
      |    }}
      |    #day-cube,
      |    button#day-cube {{
      |        background: transparent;
      |        background-color: transparent;
      |        color: {text_color};
      |    }}
      |    #day-cube:hover,
      |    button#day-cube:hover {{
      |        background-color: {cad_range_bg};
      |        color: {cad_violet};
      |    }}
      |    #day-cube-empty,
      |    button#day-cube-empty {{
      |        background: transparent;
      |        background-color: transparent;
      |        color: {cad_border_muted};
      |        border: 1px solid {cad_border_muted};
      |    }}
      |    #day-cube-range,
      |    button#day-cube-range {{
      |        background: {cad_range_bg};
      |        background-color: {cad_range_bg};
      |        color: {cad_violet};
      |        border-radius: 0px;
      |    }}
      |    #day-cube-range label,
      |    button#day-cube-range label {{
      |        color: {cad_violet};
      |        font-weight: bold;
      |    }}
      |    #day-cube-range:hover,
      |    button#day-cube-range:hover {{
      |        background: {cad_violet};
      |        background-color: {cad_violet};
      |        color: {cad_bg};
      |    }}
      |    #day-cube-range:hover label,
      |    button#day-cube-range:hover label {{
      |        color: {cad_bg};
      |    }}
      |    #day-cube-today,
      |    button#day-cube-today {{
      |        background: {cad_today_bg};
      |        background-color: {cad_today_bg};
      |        color: {cad_gold};
      |        border: 1px solid {cad_violet};
      |        font-weight: bold;
      |    }}
      |    #day-cube-today:hover,
      |    button#day-cube-today:hover {{
      |        background: {cad_violet};
      |        background-color: {cad_violet};
      |        color: {cad_bg};
      |    }}
      |    #day-cube-selected,
      |    button#day-cube-selected {{
      |        background: {cad_violet};
      |        background-color: {cad_violet};
      |        color: {cad_bg};
      |        font-weight: bold;
      |    }}
      |    #day-cube-selected label,
      |    button#day-cube-selected label {{
      |        color: {cad_bg};
      |        font-weight: bold;
      |    }}
      |    #cal-diff {{
      |        background-color: {cad_bg};
      |        border: 1px solid {cad_border_muted};
      |        border-radius: 0px;
      |        padding: 6px 8px;
      |    }}
      |    #cal-diff-title {{
      |        color: {text_color};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-size: 13px;
      |    }}
      |    #cal-footer {{
      |        background-color: {cad_footer_bg};
      |        border: 1px solid {cad_border_muted};
      |        border-radius: 0px;
      |        margin-top: 2px;
      |    }}
      |    #footer-segment {{
      |        color: {cad_text_muted};
      |        font-family: "JetBrainsMono Nerd Font", monospace;
      |        font-size: 12px;
      |        font-weight: bold;
      |        padding: 4px 10px;
      |        border-right: 1px solid {cad_footer_divider};
      |    }}
      |    #footer-segment.last-segment {{
      |        border-right: none;
      |    }}
      |    '''
      |    css_provider.load_from_data(custom_css.encode("utf-8"))
      |    Gtk.StyleContext.add_provider_for_screen(
      |        Gdk.Screen.get_default(),
      |        css_provider,
      |        Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
      |    )
      |
      |    win.show_all()
      |    Gtk.main()
      |
      |if __name__ == "__main__":
      |    main()
      |""".stripMargin
