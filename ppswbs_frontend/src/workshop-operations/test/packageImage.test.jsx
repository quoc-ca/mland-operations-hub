import React from 'react';
import { vi, describe, expect, it, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderRoutes } from '../../test/render';
import { PackageImage } from '../../workshops/PackageImage';
import WorkshopPackageManagementPage from '../pages/WorkshopPackageManagementPage';

const MEDIA = '3f1a0f6e-3a53-4a0a-9a9a-1d2f6a6f2b11';
const routes = [{ path: '/operations/packages', element: <WorkshopPackageManagementPage /> }];
const draft = body => ({ ...body, packageId: 7, state: 'DRAFT' });
const png = () => new File(['x'], 'a.png', { type: 'image/png' });

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

async function openCreateForm(handlers) {
  const calls = mockFetch({ 'GET /api/v1/workshop-package-management/packages': json(200, []), ...handlers });
  const user = userEvent.setup({ applyAccept: false });
  renderRoutes(routes, { language: 'en', path: '/operations/packages' });
  await user.click(await screen.findByRole('button', { name: 'Create the first package' }));
  await user.type(screen.getByLabelText('Name'), 'Silver');
  await user.type(screen.getByLabelText('Public price (VND)'), '500000');
  await user.type(screen.getByLabelText('Customer-safe description'), 'Hand made');
  return { calls, user };
}
const created = calls => calls.find(call => call.path === '/api/v1/workshop-packages');

/** 003.2 FR-073: one optional cover image per package. */
describe('Package cover image management', () => {
  it('uploads the chosen file, previews it and saves its media ID with the package', async () => {
    const { calls, user } = await openCreateForm({
      'POST /api/v1/catalogue-management/media-uploads': json(201, { mediaId: MEDIA }),
      'POST /api/v1/workshop-packages': call => json(201, draft(call.body)),
    });

    await user.upload(screen.getByLabelText('Cover image (optional)'), png());
    expect(await screen.findByAltText('Preview of the package cover image')).toHaveAttribute('src', 'blob:preview');
    await user.click(screen.getByRole('button', { name: 'Create draft' }));

    await waitFor(() => expect(created(calls)?.body.imageMediaId).toBe(MEDIA));
    expect(calls.find(call => call.path.endsWith('/media-uploads')).headers['Idempotency-Key']).toBeTruthy();
  });

  it('refuses another file type or a file over 5 MB without uploading, and saves without an image', async () => {
    const { calls, user } = await openCreateForm({ 'POST /api/v1/workshop-packages': call => json(201, draft(call.body)) });
    const input = screen.getByLabelText('Cover image (optional)');

    await user.upload(input, new File(['x'], 'a.gif', { type: 'image/gif' }));
    expect(screen.getByText('Only JPG, PNG or WebP images are accepted.')).toBeInTheDocument();
    const big = png();
    Object.defineProperty(big, 'size', { value: 5_000_001 });
    await user.upload(input, big);
    expect(screen.getByText('The image is larger than 5 MB.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create draft' }));

    await waitFor(() => expect(created(calls)?.body.imageMediaId).toBeNull());
    expect(calls.some(call => call.path.endsWith('/media-uploads'))).toBe(false);
  });

  it('keeps the form after a failed upload and retries with the same key', async () => {
    let fail = true;
    const { calls, user } = await openCreateForm({
      'POST /api/v1/catalogue-management/media-uploads': () => (fail
        ? json(503, { code: 'MEDIA_DEPENDENCY_UNAVAILABLE' }) : json(201, { mediaId: MEDIA })),
    });

    await user.upload(screen.getByLabelText('Cover image (optional)'), png());
    const retry = await screen.findByRole('button', { name: 'Upload again' });
    expect(screen.getByLabelText('Name')).toHaveValue('Silver');
    fail = false;
    await user.click(retry);
    expect(await screen.findByAltText('Preview of the package cover image')).toBeInTheDocument();
    const keys = calls.filter(call => call.path.endsWith('/media-uploads')).map(call => call.headers['Idempotency-Key']);
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBe(keys[1]);
  });

  it('removes the stored image of a package on save', async () => {
    const stored = { packageId: 7, state: 'PUBLISHED', name: 'Silver', description: 'Hand made', publicPrice: 500000, minParticipants: 1, maxParticipants: 4, requiredSessionCount: 1, sameLocationRequired: true, minimumSameDaySlotGap: 0, requiredFoundryWorkingDaysBetweenSessions: 0, imageMediaId: MEDIA };
    const calls = mockFetch({
      'GET /api/v1/workshop-package-management/packages': json(200, [stored]),
      'GET /api/v1/workshop-package-management/packages/7/image': () => new Response('x', { status: 200, headers: { 'Content-Type': 'image/png' } }),
      'PATCH /api/v1/workshop-packages/7': call => json(200, { ...stored, ...call.body }),
    });
    const user = userEvent.setup();
    renderRoutes(routes, { language: 'en', path: '/operations/packages' });

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    expect(await screen.findByAltText('Preview of the package cover image')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove image' }));
    expect(screen.queryByAltText('Preview of the package cover image')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(calls.find(call => call.method === 'PATCH')?.body.imageMediaId).toBeNull());
  });
});

describe('Public package image', () => {
  it('shows the published image and falls back when there is none or it cannot load', () => {
    const { rerender } = render(<PackageImage packageId={7} imageMediaId={MEDIA} alt="Silver" fallback={<span>artwork</span>} />);
    const image = screen.getByAltText('Silver');
    expect(image.getAttribute('src')).toContain(`/api/v1/workshop-packages/7/image?v=${MEDIA}`);
    fireEvent.error(image);
    expect(screen.getByText('artwork')).toBeInTheDocument();
    rerender(<PackageImage packageId={7} imageMediaId={null} alt="Silver" fallback={<span>artwork</span>} />);
    expect(screen.getByText('artwork')).toBeInTheDocument();
  });
});
