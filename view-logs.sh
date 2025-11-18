#!/bin/bash
# AI Rescue Ring - Log Viewer Script
# This script shows logs for the AIHelperActivity with filtering

echo "=== AI Rescue Ring - Debug Logs ==="
echo "Showing logs for: AIHelperActivity"
echo "Press Ctrl+C to stop"
echo "=================================="
echo ""

# Clear old logs and start fresh
adb logcat -c

# Show logs with filtering for our app
# -s = silent mode (only show specified tags)
# AIHelperActivity:D = Debug level and above for our activity
adb logcat -s AIHelperActivity:D
