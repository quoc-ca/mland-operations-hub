-- V204: Update base material unit prices for workshop ring designs (Silver: 900k, Copper: 750k, Steel: 750k)
UPDATE ring_materials 
SET unit_price = 900000.00, updated_at = NOW() 
WHERE material_code = 'MAT-SILVER-925';

UPDATE ring_materials 
SET unit_price = 750000.00, updated_at = NOW() 
WHERE material_code = 'MAT-COPPER';

UPDATE ring_materials 
SET unit_price = 750000.00, updated_at = NOW() 
WHERE material_code = 'MAT-STAINLESS-STEEL';
