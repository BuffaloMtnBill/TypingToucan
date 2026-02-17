import os
import sys
from PIL import Image, ImageDraw, ImageOps

def resize_icons(source_path):
    if not os.path.exists(source_path):
        print(f"Error: Source file '{source_path}' not found.")
        print(f"Current directory: {os.getcwd()}")
        print("Please place your 512x512 icon in this directory and name it 'icon.png', or pass the filename as an argument.")
        return

    # Android icon sizes (mipmap)
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }

    base_res_dir = os.path.join('android', 'src', 'main', 'res')

    try:
        with Image.open(source_path) as img:
            # Ensure RGBA for transparency
            img = img.convert("RGBA")
            print(f"Opened source image: {source_path} ({img.size[0]}x{img.size[1]})")
            
            for folder_name, size in sizes.items():
                target_dir = os.path.join(base_res_dir, folder_name)
                
                # Ensure directory exists
                if not os.path.exists(target_dir):
                    os.makedirs(target_dir)
                    print(f"Created directory: {target_dir}")

                # 1. Create Square Icon
                resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
                target_path = os.path.join(target_dir, 'ic_launcher.png')
                resized_img.save(target_path, 'PNG')
                print(f"Saved {size}x{size} square to {target_path}")
                
                # 2. Create Round Icon (Circular Crop)
                # Create mask
                mask = Image.new('L', (size, size), 0)
                draw = ImageDraw.Draw(mask)
                draw.ellipse((0, 0, size, size), fill=255)
                
                # Apply mask (create new transparent image, paste resized image using mask)
                round_img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
                round_img.paste(resized_img, (0, 0), mask=mask)
                
                target_round_path = os.path.join(target_dir, 'ic_launcher_round.png')
                round_img.save(target_round_path, 'PNG')
                print(f"Saved {size}x{size} round to {target_round_path}")

            print("Done! Icons generated.")

    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    source_file = 'icon.png'
    if len(sys.argv) > 1:
        source_file = sys.argv[1]
    
    resize_icons(source_file)
