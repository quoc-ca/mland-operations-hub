import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import {
  normalizePhoneNumber,
  validatePhoneNumber,
  validateAddressInput,
} from '../hooks/useMemberAddresses';
import { addressApiClient, AddressResponse } from '../api/AddressApiClient';
import AddressBookPage from '../components/AddressBookPage';
import AddressModal from '../components/AddressModal';
import { ApiError } from '../../../shared/api/http';

const mockAddresses: AddressResponse[] = [
  {
    id: 1,
    recipientName: 'Nguyễn Văn Sơn',
    phoneNumber: '0912345678',
    provinceId: '01',
    provinceName: 'Thành phố Hà Nội',
    districtId: '001',
    districtName: 'Quận Ba Đình',
    wardId: '00001',
    wardName: 'Phường Phúc Xá',
    hamletId: null,
    hamletName: null,
    detailAddress: 'Số 10, Ngõ 5 Phố Nghĩa Dũng',
    addressType: 'HOME',
    isDefault: true,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 2,
    recipientName: 'Công ty Cổ phần Mland',
    phoneNumber: '0987654321',
    provinceId: '79',
    provinceName: 'Thành phố Hồ Chí Minh',
    districtId: '760',
    districtName: 'Quận 1',
    wardId: '26734',
    wardName: 'Phường Bến Nghé',
    hamletId: null,
    hamletName: 'Tòa nhà Landmark',
    detailAddress: 'Tầng 15, Số 2 Lê Duẩn',
    addressType: 'OFFICE',
    isDefault: false,
    createdAt: '2026-11-02T14:30:00Z',
    updatedAt: '2026-11-02T14:30:00Z',
  },
];

describe('FE-001 Member Shipping Address Book (US6 / WF-09)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Phone normalization & validation (FR-038)', () => {
    it('normalizes phone number with whitespace, dots, and hyphens', () => {
      expect(normalizePhoneNumber('  0912 345 678  ')).toBe('0912345678');
      expect(normalizePhoneNumber('0912.345.678')).toBe('0912345678');
      expect(normalizePhoneNumber('0912-345-678')).toBe('0912345678');
      expect(normalizePhoneNumber('(0912) 345 678')).toBe('0912345678');
    });

    it('converts +84 and 84 prefix to standard 0 prefix', () => {
      expect(normalizePhoneNumber('+84912345678')).toBe('0912345678');
      expect(normalizePhoneNumber('+84 987 654 321')).toBe('0987654321');
      expect(normalizePhoneNumber('84912345678')).toBe('0912345678');
    });

    it('validates 10-digit Vietnamese mobile prefixes (^0[35789]\\d{8}$)', () => {
      // Valid numbers
      expect(validatePhoneNumber('0912345678')).toBe(true);
      expect(validatePhoneNumber('+84 382 123 456')).toBe(true);
      expect(validatePhoneNumber('0791234567')).toBe(true);
      expect(validatePhoneNumber('0581234567')).toBe(true);

      // Invalid: wrong length
      expect(validatePhoneNumber('091234567')).toBe(false); // 9 digits
      expect(validatePhoneNumber('09123456789')).toBe(false); // 11 digits

      // Invalid: unsupported prefix (not 3, 5, 7, 8, 9)
      expect(validatePhoneNumber('0123456789')).toBe(false);
      expect(validatePhoneNumber('0412345678')).toBe(false);

      // Invalid: letters or empty
      expect(validatePhoneNumber('abc')).toBe(false);
      expect(validatePhoneNumber('')).toBe(false);
    });
  });

  describe('validateAddressInput', () => {
    it('rejects empty required fields with friendly Vietnamese messages', () => {
      const errors = validateAddressInput({});
      expect(errors.recipientName).toBe('Họ và tên người nhận là bắt buộc');
      expect(errors.phoneNumber).toBe('Số điện thoại người nhận là bắt buộc');
      expect(errors.provinceName).toBe('Vui lòng chọn Tỉnh/Thành phố');
      expect(errors.districtName).toBe('Vui lòng chọn Quận/Huyện');
      expect(errors.wardName).toBe('Vui lòng chọn Phường/Xã');
      expect(errors.detailAddress).toBe('Địa chỉ chi tiết (số nhà, tên đường...) là bắt buộc');
    });

    it('passes for complete and valid address data', () => {
      const errors = validateAddressInput({
        recipientName: 'Nguyễn Văn A',
        phoneNumber: '+84 912 345 678',
        provinceName: 'Hà Nội',
        districtName: 'Ba Đình',
        wardName: 'Phúc Xá',
        detailAddress: 'Số 10 Đội Cấn',
      });
      expect(Object.keys(errors)).toHaveLength(0);
    });
  });

  describe('AddressBookPage (T062)', () => {
    it('renders list of addresses with default badge, type badge, and quota count', async () => {
      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue(mockAddresses);

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      // Verify quota count indicator
      expect(await screen.findByText(/Đã lưu:/i)).toBeInTheDocument();
      expect(screen.getByText('2/10')).toBeInTheDocument();

      // Verify address 1
      expect(screen.getByText('Nguyễn Văn Sơn')).toBeInTheDocument();
      expect(screen.getByText('0912345678')).toBeInTheDocument();
      expect(screen.getByText('Số 10, Ngõ 5 Phố Nghĩa Dũng')).toBeInTheDocument();
      expect(screen.getByText('Mặc định')).toBeInTheDocument();
      expect(screen.getByText('Nhà riêng')).toBeInTheDocument();

      // Verify address 2
      expect(screen.getByText('Công ty Cổ phần Mland')).toBeInTheDocument();
      expect(screen.getByText('Văn phòng')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Thiết lập mặc định' })).toBeInTheDocument();
    });

    it('shows empty state when member has no addresses', async () => {
      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue([]);

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      expect(await screen.findByText('Bạn chưa có địa chỉ nhận hàng nào')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Thêm địa chỉ đầu tiên' })).toBeInTheDocument();
    });

    it('blocks deleting default address when multiple addresses exist (FR-037)', async () => {
      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue(mockAddresses);

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      await screen.findByText('Nguyễn Văn Sơn');

      // The delete button for the default address when addresses.length > 1 should be disabled
      const deleteButtons = screen.getAllByRole('button', { name: 'Xóa' });
      // First button corresponds to default address
      expect(deleteButtons[0]).toBeDisabled();
      expect(deleteButtons[0]).toHaveAttribute(
        'title',
        'Không thể xóa địa chỉ mặc định khi còn các địa chỉ khác. Đổi mặc định trước.'
      );
    });

    it('allows deleting non-default address after confirmation (Hard Delete)', async () => {
      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue(mockAddresses);
      const deleteSpy = vi.spyOn(addressApiClient, 'deleteOwnAddress').mockResolvedValue(undefined);

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      await screen.findByText('Công ty Cổ phần Mland');

      // Click delete on second address (non-default)
      const deleteButtons = screen.getAllByRole('button', { name: 'Xóa' });
      expect(deleteButtons[1]).not.toBeDisabled();
      fireEvent.click(deleteButtons[1]);

      // Confirmation dialog should open
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Xác nhận xóa địa chỉ')).toBeInTheDocument();

      // Confirm deletion
      const confirmBtn = screen.getByRole('button', { name: 'Xác nhận xóa' });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(deleteSpy).toHaveBeenCalledWith(2);
      });
    });

    it('sets non-default address as default when clicked (FR-037)', async () => {
      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue(mockAddresses);
      const setDefaultSpy = vi.spyOn(addressApiClient, 'setDefaultAddress').mockResolvedValue({
        ...mockAddresses[1],
        isDefault: true,
      });

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      const setDefaultBtn = await screen.findByRole('button', { name: 'Thiết lập mặc định' });
      fireEvent.click(setDefaultBtn);

      await waitFor(() => {
        expect(setDefaultSpy).toHaveBeenCalledWith(2);
      });
    });

    it('disables add button when 10 address quota is reached (FR-036)', async () => {
      const tenAddresses: AddressResponse[] = Array.from({ length: 10 }, (_, i) => ({
        id: i + 1,
        recipientName: `Người nhận ${i + 1}`,
        phoneNumber: '0912345678',
        provinceId: '01',
        provinceName: 'Hà Nội',
        districtId: '001',
        districtName: 'Ba Đình',
        wardId: '00001',
        wardName: 'Phúc Xá',
        detailAddress: `Số ${i + 1}`,
        addressType: 'HOME' as const,
        isDefault: i === 0,
        createdAt: '2026-11-01',
        updatedAt: '2026-11-01',
      }));

      vi.spyOn(addressApiClient, 'getOwnAddresses').mockResolvedValue(tenAddresses);

      render(
        <MemoryRouter>
          <AddressBookPage />
        </MemoryRouter>
      );

      expect(await screen.findByText('10/10')).toBeInTheDocument();
      const addBtn = screen.getByRole('button', { name: '+ Thêm địa chỉ mới' });
      expect(addBtn).toBeDisabled();
      expect(
        screen.getByText(/Bạn đã sử dụng đủ số lượng tối đa 10 địa chỉ nhận hàng/i)
      ).toBeInTheDocument();
    });
  });

  describe('AddressModal (T062)', () => {
    it('renders with accessible modal dialog attributes and handles cascading divisions', async () => {
      vi.spyOn(addressApiClient, 'listProvinces').mockResolvedValue([
        { id: '01', name: 'Thành phố Hà Nội' },
        { id: '79', name: 'Thành phố Hồ Chí Minh' },
      ]);
      const districtSpy = vi.spyOn(addressApiClient, 'listDistricts').mockResolvedValue([
        { id: '001', name: 'Quận Ba Đình' },
        { id: '002', name: 'Quận Hoàn Kiếm' },
      ]);
      const wardSpy = vi.spyOn(addressApiClient, 'listWards').mockResolvedValue([
        { id: '00001', name: 'Phường Phúc Xá' },
      ]);

      const onSaveMock = vi.fn().mockResolvedValue(undefined);
      const onCloseMock = vi.fn();

      render(
        <AddressModal
          isOpen={true}
          onClose={onCloseMock}
          onSave={onSaveMock}
          isFirstAddress={false}
        />
      );

      // Verify WCAG accessibility attributes
      const dialog = screen.getByRole('dialog');
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(dialog).toHaveAttribute('aria-labelledby', 'address-modal-title');

      // Load provinces and select Hà Nội
      const provinceSelect = await screen.findByLabelText(/Tỉnh \/ Thành phố/i);
      await waitFor(() => {
        expect(screen.getByText('Thành phố Hà Nội')).toBeInTheDocument();
      });

      fireEvent.change(provinceSelect, { target: { value: '01' } });
      await waitFor(() => {
        expect(districtSpy).toHaveBeenCalledWith('01');
      });

      // Select District
      const districtSelect = screen.getByLabelText(/Quận \/ Huyện/i);
      await waitFor(() => {
        expect(screen.getByText('Quận Ba Đình')).toBeInTheDocument();
      });

      fireEvent.change(districtSelect, { target: { value: '001' } });
      await waitFor(() => {
        expect(wardSpy).toHaveBeenCalledWith('001');
      });

      // Select Ward
      const wardSelect = screen.getByLabelText(/Phường \/ Xã/i);
      await waitFor(() => {
        expect(screen.getByText('Phường Phúc Xá')).toBeInTheDocument();
      });
      fireEvent.change(wardSelect, { target: { value: '00001' } });

      // Fill Name, Phone, Detail Address
      fireEvent.change(screen.getByLabelText(/Họ và tên người nhận/i), {
        target: { value: 'Trần Thị B' },
      });
      fireEvent.change(screen.getByLabelText(/Số điện thoại/i), {
        target: { value: '+84 987 654 321' },
      });
      fireEvent.change(screen.getByLabelText(/Địa chỉ cụ thể/i), {
        target: { value: 'Số 15 Phố Quán Thánh' },
      });

      // Submit Form
      const submitBtn = screen.getByRole('button', { name: 'Hoàn thành' });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(onSaveMock).toHaveBeenCalledWith(
          expect.objectContaining({
            recipientName: 'Trần Thị B',
            phoneNumber: '0987654321', // Normalized!
            provinceName: 'Thành phố Hà Nội',
            districtName: 'Quận Ba Đình',
            wardName: 'Phường Phúc Xá',
            detailAddress: 'Số 15 Phố Quán Thánh',
            addressType: 'HOME',
          })
        );
      });
      expect(onCloseMock).toHaveBeenCalled();
    });

    it('pre-fills name and phone when adding first address and forces isDefault=true', async () => {
      vi.spyOn(addressApiClient, 'listProvinces').mockResolvedValue([]);

      render(
        <AddressModal
          isOpen={true}
          onClose={vi.fn()}
          onSave={vi.fn()}
          profileDefaults={{
            fullName: 'Lê Văn C',
            phone: '0901234567',
          }}
          isFirstAddress={true}
        />
      );

      const nameInput = screen.getByLabelText(/Họ và tên người nhận/i) as HTMLInputElement;
      const phoneInput = screen.getByLabelText(/Số điện thoại/i) as HTMLInputElement;

      expect(nameInput.value).toBe('Lê Văn C');
      expect(phoneInput.value).toBe('0901234567');

      // The isDefault checkbox should be checked and disabled for first address
      const checkbox = screen.getByRole('checkbox', {
        name: /Đặt làm địa chỉ giao hàng mặc định/i,
      }) as HTMLInputElement;
      expect(checkbox.checked).toBe(true);
      expect(checkbox).toBeDisabled();
    });
  });
});

