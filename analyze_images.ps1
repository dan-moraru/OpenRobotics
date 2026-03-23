Add-Type -AssemblyName System.Drawing

$base = "C:\Users\Admin\IdeaProjects\OpenRobotics\docs\images"
$out  = "C:\Users\Admin\IdeaProjects\OpenRobotics\img_analysis.txt"
$results = @()

function SamplePx($bmp, $name, $x, $y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $bmp.Width -or $y -ge $bmp.Height) {
        return "  $name at ($x,$y) = out of bounds for image ${($bmp.Width)}x${($bmp.Height)}"
    }
    $px = $bmp.GetPixel($x, $y)
    return "  $name at ($x,$y) = #$('{0:X2}{1:X2}{2:X2}' -f $px.R,$px.G,$px.B)  RGB($($px.R),$($px.G),$($px.B))"
}

# ─── RESULTS SCREEN ─────────────────────────────────────────────────
$bmp = $null
try {
    $bmp = [System.Drawing.Bitmap]::new("$base\1440p Desktop - RESULTS.png")
    $results += "=== RESULTS SCREEN ($($bmp.Width)x$($bmp.Height)) ==="
    $results += SamplePx $bmp "outer_bg"          5    5
    $results += SamplePx $bmp "topbar_bg"         100  15
    $results += SamplePx $bmp "tab_editor_btn"    80   47
    $results += SamplePx $bmp "tab_results_btn"   140  47
    $results += SamplePx $bmp "chart_panel_bg"    200  200
    $results += SamplePx $bmp "chart_header_bg"   200  75
    $results += SamplePx $bmp "table_header_bg"   200  450
    $results += SamplePx $bmp "table_row_bg"      200  490
    $results += SamplePx $bmp "table_row_alt"     200  510
    $results += SamplePx $bmp "display_settings"  100  780
    $results += SamplePx $bmp "opt_summary"       300  780
    $results += SamplePx $bmp "viewport_right_bg" 1000 300
    $results += SamplePx $bmp "mask_legend_bg"    1000 780
    $results += SamplePx $bmp "mask_red"          880  810
    $results += SamplePx $bmp "mask_yellow"       960  810
    $results += SamplePx $bmp "mask_grey"         1040 810
    $results += SamplePx $bmp "new_sim_btn"       355  870
    $results += SamplePx $bmp "view_history_btn"  540  870
    $results += SamplePx $bmp "export_btn"        1250 870
    $results += SamplePx $bmp "bottom_bar_bg"     700  900
} catch {
    $results += "=== RESULTS SCREEN ==="
    $results += "  Failed to analyze file '1440p Desktop - RESULTS.png': $($_.Exception.Message)"
} finally {
    if ($null -ne $bmp) { $bmp.Dispose() }
    $results += ""
}

# ─── SETUP SCREEN ───────────────────────────────────────────────────
$bmp = $null
try {
    $bmp = [System.Drawing.Bitmap]::new("$base\1440p Desktop - SETUP SCREEN.png")
    $results += "=== SETUP SCREEN ($($bmp.Width)x$($bmp.Height)) ==="
$results += SamplePx $bmp "outer_bg"        5    5
$results += SamplePx $bmp "topbar_bg"       100  15
$results += SamplePx $bmp "topbar_logo"     18   20
$results += SamplePx $bmp "topbar_text"     60   20
$results += SamplePx $bmp "menu_file"       183  20
$results += SamplePx $bmp "menu_help"       223  20
$results += SamplePx $bmp "tab_strip_bg"    100  47
$results += SamplePx $bmp "tab_editor_btn"  100  47
$results += SamplePx $bmp "sidebar_bg"      100  200
$results += SamplePx $bmp "prop_row_dark"   100  90
$results += SamplePx $bmp "prop_row_light"  100  115
$results += SamplePx $bmp "reset_btn"       12   90
$results += SamplePx $bmp "prop_label_txt"  60   94
$results += SamplePx $bmp "input_field_bg"  170  90
$results += SamplePx $bmp "input_field_txt" 180  94
$results += SamplePx $bmp "viewport_bg"     800  300
$results += SamplePx $bmp "viewport_grid"   700  200
$results += SamplePx $bmp "viewport_header" 700  65
$results += SamplePx $bmp "viewport_hdrtxt" 720  68
$results += SamplePx $bmp "bottom_bar_bg"   400  995
$results += SamplePx $bmp "load_btn"        820  995
$results += SamplePx $bmp "save_btn"        930  995
$results += SamplePx $bmp "start_btn"       1100 995
$results += SamplePx $bmp "start_btn_txt"   1120 995
} catch {
    $results += "=== SETUP SCREEN ==="
    $results += "  Failed to analyze file '1440p Desktop - SETUP SCREEN.png': $($_.Exception.Message)"
} finally {
    if ($null -ne $bmp) { $bmp.Dispose() }
    $results += ""
}

# ─── SIMULATION SCREEN ──────────────────────────────────────────────
$bmp = $null
try {
    $bmp = [System.Drawing.Bitmap]::new("$base\1440p Desktop - SIMULATION SCREEN.png")
    $results += "=== SIMULATION SCREEN ($($bmp.Width)x$($bmp.Height)) ==="
$results += SamplePx $bmp "outer_bg"          5    5
$results += SamplePx $bmp "topbar_bg"         100  15
$results += SamplePx $bmp "tab_strip_bg"      100  47
$results += SamplePx $bmp "tab_editor_active" 100  47
$results += SamplePx $bmp "sidebar_bg"        60   200
$results += SamplePx $bmp "add_obj_tile"      60   120
$results += SamplePx $bmp "add_obj_tile2"     115  120
$results += SamplePx $bmp "properties_header" 60   505
$results += SamplePx $bmp "prop_row_a"        60   540
$results += SamplePx $bmp "prop_row_b"        60   570
$results += SamplePx $bmp "prop_input"        170  540
$results += SamplePx $bmp "viewport_header"   700  65
$results += SamplePx $bmp "viewport_bg"       700  400
$results += SamplePx $bmp "viewport_grid_ln"  680  150
$results += SamplePx $bmp "console_header"    400  860
$results += SamplePx $bmp "console_bg"        400  900
$results += SamplePx $bmp "console_text"      420  880
$results += SamplePx $bmp "playbar_bg"        700  838
$results += SamplePx $bmp "play_btn"          260  838
$results += SamplePx $bmp "speed_btn"         295  838
$results += SamplePx $bmp "tick_label"        260  858
$results += SamplePx $bmp "tip_text"          700  1005
} catch {
    $results += "=== SIMULATION SCREEN ==="
    $results += "  Failed to analyze file '1440p Desktop - SIMULATION SCREEN.png': $($_.Exception.Message)"
} finally {
    if ($null -ne $bmp) { $bmp.Dispose() }
    $results += ""
}

# ─── WELCOME SCREEN ─────────────────────────────────────────────────
$bmp = $null
try {
    $bmp = [System.Drawing.Bitmap]::new("$base\1440p Desktop - WELCOME SCREEN.png")
    $results += "=== WELCOME SCREEN ($($bmp.Width)x$($bmp.Height)) ==="
$results += SamplePx $bmp "outer_bg"          5    5
$results += SamplePx $bmp "topbar_bg"         100  15
$results += SamplePx $bmp "dialog_bg"         720  300
$results += SamplePx $bmp "dialog_title_bar"  720  215
$results += SamplePx $bmp "dialog_title_txt"  740  218
$results += SamplePx $bmp "dialog_body"       720  350
$results += SamplePx $bmp "changelog_text"    740  340
$results += SamplePx $bmp "bottom_strip"      720  625
$results += SamplePx $bmp "click_hint"        900  628
} catch {
    $results += "=== WELCOME SCREEN ==="
    $results += "  Failed to analyze file '1440p Desktop - WELCOME SCREEN.png': $($_.Exception.Message)"
} finally {
    if ($null -ne $bmp) { $bmp.Dispose() }
    $results += ""
}

# ─── SPECIALTY SCREENS ──────────────────────────────────────────────
$bmp = $null
try {
    $bmp = [System.Drawing.Bitmap]::new("$base\Specialty Screens.png")
    $results += "=== SPECIALTY SCREENS ($($bmp.Width)x$($bmp.Height)) ==="
# Row 1: loading | error | warning | info  (y~40-240)
# Row 2: quit | load | obj-desc | save     (y~265-480)
# Bottom bar: menu items                   (y~515-674)
$results += SamplePx $bmp "outer_bg"             5    5
# Loading dialog (col1 x=0-328)
$results += SamplePx $bmp "loading_dialog_bg"    165  140
$results += SamplePx $bmp "loading_spinner"      165  155
# Error dialog (col2 x=328-657)
$results += SamplePx $bmp "error_title_bar"      490  50
$results += SamplePx $bmp "error_header_red"     490  110
$results += SamplePx $bmp "error_header_txt"     540  112
$results += SamplePx $bmp "error_body_bg"        490  180
$results += SamplePx $bmp "error_id_col"         365  112
# Warning dialog (col3 x=657-984)
$results += SamplePx $bmp "warn_header_yellow"   816  110
$results += SamplePx $bmp "warn_header_txt"      860  112
# Info dialog (col4 x=984-1311)
$results += SamplePx $bmp "info_header_green"    1140 110
$results += SamplePx $bmp "info_header_txt"      1185 112
# Row2 - Quit dialog (col1, y=265-480)
$results += SamplePx $bmp "quit_dialog_bg"       165  350
$results += SamplePx $bmp "quit_title_bar"       165  280
$results += SamplePx $bmp "quit_body_text"       165  335
$results += SamplePx $bmp "quit_yellow_link"     180  360
$results += SamplePx $bmp "quit_red_btn"         100  450
$results += SamplePx $bmp "quit_red_btn_txt"     105  453
$results += SamplePx $bmp "quit_neutral_btn"     250  450
$results += SamplePx $bmp "quit_neutral_btn_txt" 255  453
# Load config (col2 row2)
$results += SamplePx $bmp "load_dialog_bg"       490  350
$results += SamplePx $bmp "load_title_bar"       490  280
$results += SamplePx $bmp "load_dropdown_bg"     490  370
$results += SamplePx $bmp "load_confirm_btn"     420  450
$results += SamplePx $bmp "load_return_btn"      570  450
# Object desc (col3 row2)
$results += SamplePx $bmp "obj_image_bg"         814  335
$results += SamplePx $bmp "obj_add_btn"          740  450
$results += SamplePx $bmp "obj_return_btn"       890  450
# Save config (col4 row2)
$results += SamplePx $bmp "save_dialog_bg"       1140 350
$results += SamplePx $bmp "save_title_bar"       1140 280
$results += SamplePx $bmp "save_confirm_btn"     1062 450
$results += SamplePx $bmp "save_return_btn"      1213 450
# Bottom storyboard menu bar
$results += SamplePx $bmp "menu_bar_bg"          200  560
$results += SamplePx $bmp "menu_file_item"       60   587
$results += SamplePx $bmp "menu_help_item"       245  561
$results += SamplePx $bmp "menu_calc_item"       375  587
} catch {
    $results += "=== SPECIALTY SCREENS ==="
    $results += "  Failed to analyze file 'Specialty Screens.png': $($_.Exception.Message)"
} finally {
    if ($null -ne $bmp) { $bmp.Dispose() }
    $results += ""
}

$results | Out-File -FilePath $out -Encoding utf8
Write-Host "Done"

