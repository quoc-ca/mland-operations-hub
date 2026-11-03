import subprocess
import glob
import re
import json

def get_db_metadata():
    # 1. Query columns from MySQL
    cmd = [
        'docker', 'exec', 'mysql-db', 'mysql', '-u', 'root', '-psa123123', 'ppswbs_dev',
        '-e', '''
        SELECT 
            c.TABLE_NAME, 
            c.COLUMN_NAME, 
            c.ORDINAL_POSITION,
            c.COLUMN_TYPE, 
            c.IS_NULLABLE, 
            c.COLUMN_KEY, 
            c.COLUMN_DEFAULT, 
            c.EXTRA, 
            c.COLUMN_COMMENT
        FROM information_schema.COLUMNS c
        WHERE c.TABLE_SCHEMA = 'ppswbs_dev' AND c.TABLE_NAME != 'flyway_schema_history'
        ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
        '''
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    lines = [l for l in res.stdout.strip().split('\n') if not l.startswith('mysql:')]
    header = lines[0].split('\t')
    
    tables = {}
    for line in lines[1:]:
        parts = line.split('\t')
        row = dict(zip(header, parts))
        tbl = row['TABLE_NAME'].lower()
        if tbl not in tables:
            tables[tbl] = []
        tables[tbl].append(row)

    # 2. Query foreign keys
    cmd_fk = [
        'docker', 'exec', 'mysql-db', 'mysql', '-u', 'root', '-psa123123', 'ppswbs_dev',
        '-e', '''
        SELECT 
            TABLE_NAME, 
            COLUMN_NAME, 
            REFERENCED_TABLE_NAME, 
            REFERENCED_COLUMN_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = 'ppswbs_dev' AND REFERENCED_TABLE_NAME IS NOT NULL;
        '''
    ]
    res_fk = subprocess.run(cmd_fk, capture_output=True, text=True, check=True)
    fk_lines = [l for l in res_fk.stdout.strip().split('\n') if not l.startswith('mysql:')]
    fk_header = fk_lines[0].split('\t')
    fks = {}
    for line in fk_lines[1:]:
        parts = line.split('\t')
        row = dict(zip(fk_header, parts))
        tbl = row['TABLE_NAME'].lower()
        col = row['COLUMN_NAME'].lower()
        fks[(tbl, col)] = f"{row['REFERENCED_TABLE_NAME']}.{row['REFERENCED_COLUMN_NAME']}"

    # 3. Query unique indexes
    cmd_uq = [
        'docker', 'exec', 'mysql-db', 'mysql', '-u', 'root', '-psa123123', 'ppswbs_dev',
        '-e', '''
        SELECT 
            TABLE_NAME, 
            COLUMN_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'ppswbs_dev' AND NON_UNIQUE = 0 AND INDEX_NAME != 'PRIMARY';
        '''
    ]
    res_uq = subprocess.run(cmd_uq, capture_output=True, text=True, check=True)
    uq_lines = [l for l in res_uq.stdout.strip().split('\n') if not l.startswith('mysql:')]
    uqs = set()
    for line in uq_lines[1:]:
        parts = line.split('\t')
        uqs.add((parts[0].lower(), parts[1].lower()))

    # 4. Map Java entities
    java_entities = {}
    for f in glob.glob('ppswbs_backend/src/main/java/**/*.java', recursive=True):
        content = open(f, encoding='utf-8').read()
        m = re.search(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', content)
        if m:
            tbl = m.group(1).lower()
            pkg_m = re.search(r'package\s+([^;]+);', content)
            pkg = pkg_m.group(1) if pkg_m else ''
            
            # extract class doc / description if any
            cls_m = re.search(r'(/\*\*.*?\*/)\s*(?:@\w+.*?\s*)*public\s+(?:class|record)', content, re.DOTALL)
            cls_doc = cls_m.group(1) if cls_m else ''
            java_entities[tbl] = {
                'file': f,
                'package': pkg,
                'class': re.search(r'public\s+(?:class|record)\s+(\w+)', content).group(1) if re.search(r'public\s+(?:class|record)\s+(\w+)', content) else '',
                'doc': cls_doc
            }

    print(f"Loaded {len(tables)} tables from MySQL.")
    print(f"Loaded {len(fks)} foreign key mappings.")
    print(f"Loaded {len(uqs)} unique column constraints.")
    print(f"Loaded {len(java_entities)} Java entity mappings.")
    return tables, fks, uqs, java_entities

if __name__ == '__main__':
    get_db_metadata()

