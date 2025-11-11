#!/usr/bin/env python3
"""
Generate Android app icons from a source icon file.
Creates icons for all required densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
"""

from PIL import Image
import os

# Source icon
SOURCE_ICON = "app/src/main/res/Icon.png"

# Icon sizes for different densities
ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Output directories
MIPMAP_DIR = "app/src/main/res"

def generate_icons():
    """Generate all icon sizes from the source icon."""
    print(f"Loading source icon: {SOURCE_ICON}")

    # Open source icon
    try:
        source_img = Image.open(SOURCE_ICON)
        print(f"Source icon size: {source_img.size}")
    except Exception as e:
        print(f"Error loading source icon: {e}")
        return

    # Convert to RGBA if needed
    if source_img.mode != 'RGBA':
        source_img = source_img.convert('RGBA')

    # Generate icons for each density
    for density, size in ICON_SIZES.items():
        print(f"\nGenerating {density} icons ({size}x{size})...")

        # Create output directory if it doesn't exist
        output_dir = os.path.join(MIPMAP_DIR, f"mipmap-{density}")
        os.makedirs(output_dir, exist_ok=True)

        # Resize image with high-quality resampling
        resized_img = source_img.resize((size, size), Image.Resampling.LANCZOS)

        # Save as PNG (standard launcher icon)
        png_path = os.path.join(output_dir, "ic_launcher.png")
        resized_img.save(png_path, "PNG", optimize=True)
        print(f"  ✓ Saved: {png_path}")

        # Save as round icon
        round_path = os.path.join(output_dir, "ic_launcher_round.png")
        resized_img.save(round_path, "PNG", optimize=True)
        print(f"  ✓ Saved: {round_path}")

        # Optionally save as WebP format (more efficient)
        webp_path = os.path.join(output_dir, "ic_launcher.webp")
        resized_img.save(webp_path, "WEBP", quality=95)
        print(f"  ✓ Saved: {webp_path}")

        # Save round as WebP
        webp_round_path = os.path.join(output_dir, "ic_launcher_round.webp")
        resized_img.save(webp_round_path, "WEBP", quality=95)
        print(f"  ✓ Saved: {webp_round_path}")

    print("\n✓ All icons generated successfully!")

if __name__ == "__main__":
    generate_icons()
