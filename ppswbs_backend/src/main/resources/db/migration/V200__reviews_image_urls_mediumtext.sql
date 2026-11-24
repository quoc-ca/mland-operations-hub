-- Review photos are stored as data URLs (base64) in a JSON array; TEXT (64 KB) rejected a single ordinary photo.
ALTER TABLE reviews MODIFY COLUMN image_urls MEDIUMTEXT NULL;
