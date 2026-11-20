import assert from 'node:assert';
import { parseEnvContent } from '../src/envParser.js';

console.log('--- Testing parseEnvContent ---');

// Test 1: Simple key=value
{
  const input = 'PORT=3000\nHOST=localhost';
  const result = parseEnvContent(input);
  assert.deepStrictEqual(result, [
    { key: 'PORT', value: '3000' },
    { key: 'HOST', value: 'localhost' }
  ]);
  console.log('✔ Test 1: Simple key=value passed');
}

// Test 2: Comments and empty lines
{
  const input = '# Database config\n\nDB_NAME=mland\n# Another comment\nDB_PORT=3306\n';
  const result = parseEnvContent(input);
  assert.deepStrictEqual(result, [
    { key: 'DB_NAME', value: 'mland' },
    { key: 'DB_PORT', value: '3306' }
  ]);
  console.log('✔ Test 2: Comments & empty lines ignored');
}

// Test 3: Export prefix and quotes
{
  const input = 'export API_KEY="secret-123"\nexport APP_TITLE=\'MLAND Hub\'';
  const result = parseEnvContent(input);
  assert.deepStrictEqual(result, [
    { key: 'API_KEY', value: 'secret-123' },
    { key: 'APP_TITLE', value: 'MLAND Hub' }
  ]);
  console.log('✔ Test 3: Export prefix & quotes unwrapped');
}

// Test 4: Value containing '=' signs (e.g. database URL or JWT)
{
  const input = 'DATABASE_URL=postgres://user:pass@host:5432/db?sslmode=disable&foo=bar';
  const result = parseEnvContent(input);
  assert.deepStrictEqual(result, [
    { key: 'DATABASE_URL', value: 'postgres://user:pass@host:5432/db?sslmode=disable&foo=bar' }
  ]);
  console.log('✔ Test 4: Value with multiple "=" parsed correctly');
}

// Test 5: Key without equals (specified by user to import key with empty string value)
{
  const input = 'MISSING_VALUE_KEY\nEMPTY_VAL=';
  const result = parseEnvContent(input);
  assert.deepStrictEqual(result, [
    { key: 'MISSING_VALUE_KEY', value: '' },
    { key: 'EMPTY_VAL', value: '' }
  ]);
  console.log('✔ Test 5: Keys without value handled properly');
}

// Test 6: Empty or invalid input
{
  assert.deepStrictEqual(parseEnvContent(''), [{ key: '', value: '' }]);
  assert.deepStrictEqual(parseEnvContent(null), [{ key: '', value: '' }]);
  assert.deepStrictEqual(parseEnvContent('   \n# only comments\n   '), [{ key: '', value: '' }]);
  console.log('✔ Test 6: Edge cases / fallback passed');
}

console.log('ALL PARSER TESTS PASSED SUCCESSFULLY! 🎉');
