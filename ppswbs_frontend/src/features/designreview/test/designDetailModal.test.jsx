import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import Design3dDetailModal, { DesignDetailModal } from '../components/Design3dDetailModal';

describe('DesignDetailModal (Design3dDetailModal)', () => {
  const sampleSnapshot = {
    metalType: 'Vàng 18K',
    ringSize: 14,
    bandWidthMm: 3.5,
    thicknessMm: 2.0,
    components: [
      { label: 'Kim cương chủ 1.0ct', angleDegrees: 0, quantity: 1 }
    ],
    engraving: {
      text: 'Mland Forever',
      placement: 'INNER_BAND',
      customImages: [
        { id: 'img-1', name: 'Cỏ 4 lá', previewDataUrl: 'data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=' }
      ]
    }
  };

  it('renders correctly when called with designSnapshot and designReference props', () => {
    const handleClose = vi.fn();
    render(
      <Design3dDetailModal
        isOpen={true}
        onClose={handleClose}
        designSnapshot={sampleSnapshot}
        designReference="DR-2026-001"
      />
    );

    expect(screen.getByTestId('design-3d-detail-modal')).toBeInTheDocument();
    expect(screen.getByText('DR-2026-001')).toBeInTheDocument();
    expect(screen.getByText(/Chi tiết Bản vẽ Thiết kế & Thông số Kỹ thuật/)).toBeInTheDocument();
    expect(screen.getByText(/Size 14/)).toBeInTheDocument();
    expect(screen.getByText(/Kim cương chủ 1.0ct/)).toBeInTheDocument();
    expect(screen.getByText(/"Mland Forever"/)).toBeInTheDocument();
    expect(screen.getByText('Cỏ 4 lá')).toBeInTheDocument();

    const closeBtn = screen.getByTestId('close-3d-modal-btn');
    fireEvent.click(closeBtn);
    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it('renders correctly when called with legacy snapshot and reference props', () => {
    render(
      <DesignDetailModal
        isOpen={true}
        onClose={() => {}}
        snapshot={sampleSnapshot}
        reference="CO-REF-123"
      />
    );

    expect(screen.getByText('CO-REF-123')).toBeInTheDocument();
    expect(screen.getByText(/Size 14/)).toBeInTheDocument();
  });

  it('returns null when isOpen is false', () => {
    const { container } = render(
      <Design3dDetailModal
        isOpen={false}
        onClose={() => {}}
        snapshot={sampleSnapshot}
      />
    );
    expect(container.firstChild).toBeNull();
  });
});
