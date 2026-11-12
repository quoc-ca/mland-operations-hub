// Public port of the product-management feature (FRONTEND-ARCHITECTURE §2): the only module other code may import.
export { productManagementRoutes } from './routes/productManagementRoutes';
// FR-058: the shared public product card (grid, or a sideways strip with `standalone`) for other pages such as the
// Ready Rings strip of the home page.
export { PublicProductGrid } from './ui/public/PublicProductGrid';
