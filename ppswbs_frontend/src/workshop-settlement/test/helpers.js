import { beforeEach } from 'vitest';
import { clearRoleCache } from '../../shared/auth/useRole';
import { json } from '../../test/render';

export const PROFILE = 'GET /api/v1/accounts/me/profile';
export const asRole = role => json(200, { role });
export const useFreshRole = () => beforeEach(() => clearRoleCache());
