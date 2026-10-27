"""Check the actual migration SQL with SQLite and both exported Room schemas.

This verifies schema/data preservation, not an on-device Room instrumentation run.
"""
from pathlib import Path
import json
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
schemas = root / 'app/schemas/ru.subtrack.Database'
old = json.loads((schemas / '1.json').read_text())['database']
new = json.loads((schemas / '2.json').read_text())['database']
old_entities = {e['tableName']: e for e in old['entities']}
new_entities = {e['tableName']: e for e in new['entities']}
assert new_entities == {k: v for k, v in old_entities.items() if k != 'rates'}

db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys = ON')
for name, entity in old_entities.items():
    db.execute(entity['createSql'].replace('${TABLE_NAME}', name))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', name))
db.execute('INSERT INTO users VALUES (?, ?, ?, ?, ?)', (1, 'Test', 'test@example.test', 'salt', 'hash'))
for row in [(1, 1, 'Ruble', 29900, 'RUB', 'MONTH', '2026-11-01', 'Прочее', 0, 0),
            (2, 1, 'Legacy', 1500, 'USD', 'MONTH', '2026-11-01', 'Прочее', 0, 1)]:
    db.execute('INSERT INTO subscriptions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', row)
db.execute('INSERT INTO rates VALUES (?, ?, ?, ?)', ('USD', '90', '27.10.2026', 1))
before = {t: db.execute(f'SELECT * FROM {t}').fetchall() for t in new_entities}
code = (root / 'app/src/main/java/ru/subtrack/Data.kt').read_text(encoding='utf-8')
block = code.split('val MIGRATION_1_2 =', 1)[1].split('val Context.preferences', 1)[0]
statements = re.findall(r'db\.execSQL\("([^"]+)"\)', block)
assert statements
for sql in statements:
    db.execute(sql)
assert not db.execute("SELECT 1 FROM sqlite_master WHERE name='rates'").fetchone()
for table, rows in before.items():
    assert db.execute(f'SELECT * FROM {table}').fetchall() == rows
assert not db.execute('PRAGMA foreign_key_check').fetchall()
result = {'passed': True, 'oldVersion': old['version'], 'newVersion': new['version'],
          'checks': ['new schema matches retained tables', 'users preserved',
                     'ruble and legacy records preserved', 'unused cache removed',
                     'foreign keys valid'], 'nativeRoomInstrumentation': False}
out = root / 'docs/verification/migration-check.json'
out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: SQLite migration 1 -> 2 preserves users and subscriptions')
