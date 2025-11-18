#!/bin/bash
# AI Rescue Ring - Log Viewer Script
# This script shows logs for the ChatOverlayManager (the ACTUAL overlay window)

echo "=== AI Rescue Ring - Debug Logs ==="
echo "Showing logs for: ChatOverlayManager (the actual floating window)"
echo "Press Ctrl+C to stop"
echo "=================================="
echo ""

# Clear old logs and start fresh
adb logcat -c

# Show logs with filtering for our app
# ChatOverlayManager is the actual window being shown
adb logcat -s ChatOverlayManager:D
