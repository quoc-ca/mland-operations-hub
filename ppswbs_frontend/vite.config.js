import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

function mockDesignApiPlugin() {
  const designs = new Map();
  return {
    name: 'mock-design-api',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url || '';
        if (url.startsWith('/api/v1/accounts/me')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({
            id: 1,
            email: 'member.test@example.com',
            role: 'MEMBER',
            fullName: 'Member Test',
          }));
          return;
        }

        if (url.startsWith('/api/v1/workshop-design-options')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({
            packageReference: 'PKG-COUPLE-SILVER',
            branchReference: 'BR-HCM-01',
            materials: [
              { reference: 'MAT-SILVER-925', name: 'Bạc Ý 925', description: 'Bạc cao cấp chuẩn 92.5% sáng bóng', unitPrice: 200000, currency: 'VND', available: true },
              { reference: 'MAT-COPPER', name: 'Đồng Đỏ / Đồng Thau', description: 'Đồng nguyên chất vintage', unitPrice: 150000, currency: 'VND', available: true },
              { reference: 'MAT-STAINLESS-STEEL', name: 'Thép Không Gỉ 316L', description: 'Thép y tế 316L siêu bền', unitPrice: 180000, currency: 'VND', available: true },
            ],
            shapes: [
              { reference: 'SHP-CLASSIC-ROUND', name: 'Tròn cổ điển', description: 'Dáng nhẫn tròn trơn mượt mà', priceAdjustment: 0, available: true },
              { reference: 'SHP-TWISTED', name: 'Dáng xoắn vô cực', description: 'Dáng xoắn biểu tượng tình yêu vĩnh cửu', priceAdjustment: 50000, available: true },
              { reference: 'SHP-BALL-PEEN', name: 'Bề mặt búa bi', description: 'Kết cấu búa bi độc đáo', priceAdjustment: 90000, available: true },
            ],
            sizes: Array.from({ length: 24 }, (_, i) => String(i + 6)),
            components: [
              { reference: 'GEM-CZ-WHITE-2MM', name: 'Đá CZ Trắng 2mm', description: 'Đá nhân tạo lấp lánh giác cắt kim cương', unitPrice: 50000, maxQuantity: 10, available: true },
              { reference: 'GEM-CZ-BLUE-2MM', name: 'Đá CZ Xanh Lam 2mm', description: 'Đá CZ xanh sapphire', unitPrice: 60000, maxQuantity: 10, available: true },
            ],
            attachments: [
              { reference: 'ATT-BEAD-EDGE', name: 'Hạt viền Milgrain', description: 'Chạm viền bi cổ điển vintage', unitPrice: 40000, available: true },
            ],
            models: [
              { modelReference: 'MOD-CLASSIC-ROUND', name: 'Mẫu Nhẫn Cưới Classic', description: 'Thiết kế tinh giản', materialReference: 'MAT-SILVER-925', shapeReference: 'SHP-CLASSIC-ROUND', estimatedPrice: 200000, difficultyScore: 2.0 },
            ],
            engraving: {
              standardEngravingFee: 80000,
              customImagePrice: 100000,
              maxCharacters: 50,
              maxItems: 4,
              maxTextItems: 2,
              maxSymbolItems: 2,
              maxCustomImageItems: 2,
              imagePreprocessing: {
                minContrast: 0,
                maxContrast: 100,
                defaultContrast: 50,
                minSharpness: 0,
                maxSharpness: 100,
                defaultSharpness: 50,
              },
            },
          }));
          return;
        }

        if (req.method === 'POST' && url.startsWith('/api/v1/workshop-designs') && !url.includes('/versions')) {
          let body = '';
          req.on('data', chunk => { body += chunk; });
          req.on('end', () => {
            const data = JSON.parse(body || '{}');
            const designId = 'DSG-TEST-' + Math.floor(Math.random() * 10000);
            designs.set(designId, {
              designId,
              packageReference: data.packageReference || 'PKG-COUPLE-SILVER',
              branchReference: data.branchReference || 'BR-HCM-01',
              status: 'DRAFT',
              designVersion: 1,
              componentSnapshot: data,
              estimatedPrice: 350000,
              difficultyScore: 2.5,
              updatedAt: new Date().toISOString(),
            });
            res.setHeader('Content-Type', 'application/json');
            res.statusCode = 201;
            res.end(JSON.stringify({
              designId,
              designVersion: 1,
              guestSession: 'guest-session-123',
              status: 'DRAFT',
            }));
          });
          return;
        }

        if (req.method === 'POST' && url.includes('/versions')) {
          const match = url.match(/\/api\/v1\/workshop-designs\/([^/]+)\/versions/);
          const designId = match ? match[1] : 'DSG-TEST';
          let body = '';
          req.on('data', chunk => { body += chunk; });
          req.on('end', () => {
            const data = JSON.parse(body || '{}');
            const existing = designs.get(designId) || {
              designId,
              packageReference: 'PKG-COUPLE-SILVER',
              status: 'DRAFT',
              designVersion: 1,
            };
            const updated = {
              ...existing,
              componentSnapshot: data,
              designVersion: (existing.designVersion || 1) + 1,
              updatedAt: new Date().toISOString(),
            };
            designs.set(designId, updated);
            res.setHeader('Content-Type', 'application/json');
            res.statusCode = 200;
            res.end(JSON.stringify({
              designId,
              designVersion: updated.designVersion,
              status: 'DRAFT',
            }));
          });
          return;
        }

        if (req.method === 'GET' && url.startsWith('/api/v1/workshop-designs/')) {
          const designId = url.replace('/api/v1/workshop-designs/', '').split('?')[0];
          const design = designs.get(designId) || {
            designId,
            packageReference: 'PKG-COUPLE-SILVER',
            branchReference: 'BR-HCM-01',
            status: 'DRAFT',
            designVersion: 2,
            componentSnapshot: {
              materialReference: 'MAT-SILVER-925',
              shapeReference: 'SHP-CLASSIC-ROUND',
              ringSize: '8',
              components: [],
              attachments: [],
              engraving: { enabled: true, items: [], customImages: [] },
            },
            estimatedPrice: 350000,
            difficultyScore: 2.5,
            updatedAt: new Date().toISOString(),
          };
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify(design));
          return;
        }

        next();
      });
    },
  };
}

// Frontend foundation (002 T088): build with Vite, test with Vitest + Testing Library on jsdom.
export default defineConfig({
  plugins: [react(), mockDesignApiPlugin()],
  server: {
    host: true,
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/__/auth': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
      '/__/firebase': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
    },
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
    watch: {
      usePolling: process.env.USE_POLLING === 'true',
    },
  },
  preview: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
    proxy: {
      '/__/auth': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
      '/__/firebase': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
    },
  },
  // The existing local .env came from Firebase's generated snippet and uses
  // unprefixed public config names. Keep VITE_* as the canonical format while
  // exposing only these known Firebase Web config keys for compatibility.
  envPrefix: [
    'VITE_',
    'apiKey',
    'authDomain',
    'projectId',
    'storageBucket',
    'messagingSenderId',
    'appId',
    'measurementId',
  ],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    css: false,
    restoreMocks: true,
  },
});
