import glob, xml.etree.ElementTree as ET, sys
rows = []
for f in sorted(glob.glob(sys.argv[1] + '/TEST-*.xml')):
    root = ET.parse(f).getroot()
    for tc in root.iter('testcase'):
        status = 'Pass'
        if tc.find('failure') is not None or tc.find('error') is not None:
            status = 'Fail'
        rows.append((tc.get('classname').split('.')[-1], tc.get('name'), tc.get('time'), status))
with open(sys.argv[1] + '/test-summary.md', 'w', encoding='utf-8') as out:
    out.write('| Class | Test | Time (s) | Result |\n|---|---|---|---|\n')
    for r in rows:
        out.write('| %s | %s | %s | %s |\n' % r)
print(len(rows), sum(1 for r in rows if r[3] == 'Pass'))
