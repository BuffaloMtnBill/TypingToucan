
try:
    with open('core/src/main/kotlin/com/typingtoucan/screens/GameScreen.kt', 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    balance = 0
    
    for i, line in enumerate(lines):
        code = line.split('//')[0] 
        open_b = code.count('{')
        close_b = code.count('}')
        balance += open_b
        balance -= close_b
        
    print(f"Final File Balance: {balance}")
except Exception as e:
    print(f"Error: {e}")
