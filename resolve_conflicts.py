
import os

def resolve(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    resolved_lines = []
    mode = 'normal' # normal, upstream, stashed
    
    conflict_count = 0
    
    for line in lines:
        if line.startswith('<<<<<<<'):
            mode = 'upstream'
            conflict_count += 1
        elif line.startswith('======='):
            if mode == 'upstream':
                mode = 'stashed'
            else:
                # Should not happen unless malformed
                resolved_lines.append(line)
        elif line.startswith('>>>>>>>'):
            if mode == 'stashed':
                mode = 'normal'
            else:
                 resolved_lines.append(line)
        else:
            if mode == 'normal':
                resolved_lines.append(line)
            elif mode == 'upstream':
                pass # Discard
            elif mode == 'stashed':
                resolved_lines.append(line) # Keep
                
    print(f"Resolved {conflict_count} conflicts.")
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(resolved_lines)

resolve('core/src/main/kotlin/com/typingtoucan/screens/GameScreen.kt')
