import sys

p = r'D:\Documents\Default Project\hu-launcher\dashboard\index.html'
s = open(p, 'rb').read().decode('utf-8')
print('bytes', len(s.encode('utf-8')))
print('mojibake:', s.count('\u00c3'), s.count('\u00e2\u20ac'), s.count('\u00c2'))
print('repl:', s.count('\ufffd'))
print('versi 1.0.2:', 'Versi 1.0.2' in s)
print('satellite default:', "mapMode: 'satellite'" in s)
print('cfgRev 2:', 'CFG_REV = 2' in s)
print('dockPickOverlay:', s.count('dockPickOverlay'))
print('fixText calls:', s.count('fixText('))
print('openAppInfo:', s.count('openAppInfo'))
print('notifHelp:', s.count('notifHelp'))
