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
      expect(errors.communeName).toBe('Vui lòng chọn Phường/Xã/Khu vực đặc thù');
      expect(errors.detailAddress).toBe('Địa chỉ chi tiết (số nhà, tên đường...) là bắt buộc');
    });

    it('rejects ISO control characters in recipient name and detail address (FR-040)', () => {
      const nameErrors = validateAddressInput({
        recipientName: 'Nguyễn\u0000Văn A',
        phoneNumber: '0912345678',
        provinceCode: '01',
        communeCode: '00001',
        detailAddress: 'Số 10 Đội Cấn',
      });
      expect(nameErrors.recipientName).toBe('Họ và tên không được chứa ký tự điều khiển ISO');

      const detailErrors = validateAddressInput({
        recipientName: 'Nguyễn Văn A',
        phoneNumber: '0912345678',
        provinceCode: '01',
        communeCode: '00001',
        detailAddress: 'Số 10\u001FĐội Cấn',
      });
      expect(detailErrors.detailAddress).toBe('Địa chỉ chi tiết không được chứa ký tự điều khiển ISO');
    });

    it('passes for complete and valid address data in 2-level model', () => {
      const errors = validateAddressInput({
        recipientName: 'Nguyễn Văn A',
        phoneNumber: '+84 912 345 678',
        provinceCode: '01',
        provinceName: 'Thành phố Hà Nội',
        communeCode: '00001',
        communeName: 'Phường Phúc Xá',
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

  describe('AddressModal (T062 / FR-036 / FR-037 / FR-041)', () => {
    it('renders with accessible modal dialog attributes and handles 2-level cascading divisions with local filtering', async () => {
      vi.spyOn(addressApiClient, 'listProvinces').mockResolvedValue([
        { id: '01', name: 'Thành phố Hà Nội', code: '01' },
        { id: '79', name: 'Thành phố Hồ Chí Minh', code: '79' },
      ]);
      const communeSpy = vi.spyOn(addressApiClient, 'listCommunes').mockResolvedValue([
        { id: '00001', name: 'Phường Phúc Xá', code: '00001' },
        { id: '00004', name: 'Phường Trúc Bạch', code: '00004' },
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

      // Step 1: Fill Name to unlock Phone
      const nameInput = screen.getByLabelText(/Họ và tên người nhận/i);
      fireEvent.change(nameInput, { target: { value: 'Trần Thị B' } });

      // Step 2: Fill Phone to unlock Province
      const phoneInput = screen.getByLabelText(/Số điện thoại/i);
      expect(phoneInput).not.toBeDisabled();
      fireEvent.change(phoneInput, { target: { value: '+84 987 654 321' } });

      // Step 3: Select Province
      const provinceSelect = await screen.findByLabelText(/Tỉnh \/ Thành phố/i);
      expect(provinceSelect).not.toBeDisabled();
      await waitFor(() => {
        expect(screen.getByText('Thành phố Hà Nội')).toBeInTheDocument();
      });

      fireEvent.change(provinceSelect, { target: { value: '01' } });
      await waitFor(() => {
        expect(communeSpy).toHaveBeenCalledWith('01');
      });

      // Step 4: Select Commune (2-level: no District in between)
      const communeSelect = screen.getByLabelText(/Phường \/ Xã \/ Khu vực đặc thù/i);
      await waitFor(() => {
        expect(screen.getByText('Phường Phúc Xá')).toBeInTheDocument();
      });
      fireEvent.change(communeSelect, { target: { value: '00001' } });

      // Step 5: Fill Detail Address
      const detailInput = screen.getByLabelText(/Địa chỉ cụ thể/i);
      expect(detailInput).not.toBeDisabled();
      fireEvent.change(detailInput, { target: { value: 'Số 15 Phố Quán Thánh' } });

      // Submit Form
      const submitBtn = screen.getByRole('button', { name: 'Hoàn thành' });
      expect(submitBtn).not.toBeDisabled();
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(onSaveMock).toHaveBeenCalledWith(
          expect.objectContaining({
            recipientName: 'Trần Thị B',
            phoneNumber: '0987654321', // Normalized per FR-040!
            provinceCode: '01',
            communeCode: '00001',
            detailAddress: 'Số 15 Phố Quán Thánh',
            addressType: 'HOME',
          })
        );
      });
      expect(onCloseMock).toHaveBeenCalled();
    });

    it('enforces Shopee-style progressive entry order and resets subordinate fields on change (FR-037)', async () => {
      vi.spyOn(addressApiClient, 'listProvinces').mockResolvedValue([
        { id: '01', name: 'Thành phố Hà Nội', code: '01' },
        { id: '79', name: 'Thành phố Hồ Chí Minh', code: '79' },
      ]);
      vi.spyOn(addressApiClient, 'listCommunes').mockResolvedValue([
        { id: '00001', name: 'Phường Phúc Xá', code: '00001' },
      ]);

      render(
        <AddressModal
          isOpen={true}
          onClose={vi.fn()}
          onSave={vi.fn()}
          isFirstAddress={false}
        />
      );

      const nameInput = screen.getByLabelText(/Họ và tên người nhận/i) as HTMLInputElement;
      const phoneInput = screen.getByLabelText(/Số điện thoại/i) as HTMLInputElement;
      const provinceSelect = screen.getByLabelText(/Tỉnh \/ Thành phố/i) as HTMLSelectElement;
      const communeSelect = screen.getByLabelText(/Phường \/ Xã \/ Khu vực đặc thù/i) as HTMLSelectElement;
      const detailInput = screen.getByLabelText(/Địa chỉ cụ thể/i) as HTMLInputElement;
      const submitBtn = screen.getByRole('button', { name: 'Hoàn thành' }) as HTMLButtonElement;

      // Initially: only Name is enabled
      expect(nameInput).not.toBeDisabled();
      expect(phoneInput).toBeDisabled();
      expect(provinceSelect).toBeDisabled();
      expect(communeSelect).toBeDisabled();
      expect(detailInput).toBeDisabled();
      expect(submitBtn).toBeDisabled();

      // Enter name -> unlocks phone
      fireEvent.change(nameInput, { target: { value: 'Nguyễn Văn Tiến' } });
      expect(phoneInput).not.toBeDisabled();
      expect(provinceSelect).toBeDisabled();

      // Enter invalid phone -> province stays disabled
      fireEvent.change(phoneInput, { target: { value: '123' } });
      expect(provinceSelect).toBeDisabled();

      // Enter valid phone -> unlocks province
      fireEvent.change(phoneInput, { target: { value: '0981234567' } });
      await waitFor(() => {
        expect(provinceSelect).not.toBeDisabled();
      });
      expect(communeSelect).toBeDisabled();

      // Select province -> unlocks commune
      fireEvent.change(provinceSelect, { target: { value: '01' } });
      await waitFor(() => {
        expect(communeSelect).not.toBeDisabled();
      });
      expect(detailInput).toBeDisabled();

      // Select commune -> unlocks detail address
      fireEvent.change(communeSelect, { target: { value: '00001' } });
      expect(detailInput).not.toBeDisabled();
      expect(submitBtn).toBeDisabled();

      // Enter detail address -> unlocks submit
      fireEvent.change(detailInput, { target: { value: '123 Đường Láng' } });
      expect(submitBtn).not.toBeDisabled();

      // Changing province clears commune and detail address per FR-037
      fireEvent.change(provinceSelect, { target: { value: '79' } });
      expect(communeSelect.value).toBe('');
      expect(detailInput.value).toBe('');
      expect(detailInput).toBeDisabled();
      expect(submitBtn).toBeDisabled();
    });

    it('does NOT pre-fill name or phone from profile when adding new address even if profileDefaults exist (FR-036)', async () => {
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

      // FR-036: Form MUST be completely blank, NO profile prefill!
      expect(nameInput.value).toBe('');
      expect(phoneInput.value).toBe('');
      expect(phoneInput).toBeDisabled();

      // The isDefault checkbox should be checked and disabled for first address
      const checkbox = screen.getByRole('checkbox', {
        name: /Đặt làm địa chỉ giao hàng mặc định/i,
      }) as HTMLInputElement;
      expect(checkbox.checked).toBe(true);
      expect(checkbox).toBeDisabled();
    });
  });
});

