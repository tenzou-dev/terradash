import zipfile, hashlib, sys

ver = sys.argv[1] if len(sys.argv) > 1 else '1.0.3'
apk = r'D:\Documents\Default Project\hu-launcher\android\dist\TerraDash-%s.apk' % ver
src = r'D:\Documents\Default Project\hu-launcher\dashboard\index.html'

z = zipfile.ZipFile(apk)
names = z.namelist()
data = z.read('assets/index.html')
s = data.decode('utf-8')
raw = open(src, 'rb').read()
print('entries:', len(names))
print('apk index bytes:', len(data), '| src bytes:', len(raw))
print('identical:', hashlib.sha256(data).hexdigest() == hashlib.sha256(raw).hexdigest())
print('versi ' + ver + ':', ('Versi ' + ver) in s)
print('satellite default:', "mapMode: 'satellite'" in s)
print('fixText calls:', s.count('fixText('))
print('nominatim:', s.count('geocodeNominatim'))
print('syncPlayerWidth:', s.count('syncPlayerWidth'))
print('no-player css:', s.count('body.no-player'))
print('mojibake:', s.count('\u00c3'), s.count('\u00e2\u20ac'), s.count('\u00c2'))
print('vendor leaflet:', 'assets/vendor/leaflet.js' in names)
print('unpkg refs:', s.count('unpkg.com'))
