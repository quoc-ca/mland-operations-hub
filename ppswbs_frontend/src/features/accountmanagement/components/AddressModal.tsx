import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  AddressResponse,
  AddressCreateRequest,
  AddressUpdateRequest,
} from '../api/AddressApiClient';
import {
  validateAddressInput,
  normalizePhoneNumber,
  validatePhoneNumber,
  removeDiacritics,
  useAdministrativeDivisions,
} from '../hooks/useMemberAddresses';
import '../accountmanagement.css';

/**
 * Why a phone number is not accepted, worded for the field, or '' when it is valid. The rule is the same as
 * validatePhoneNumber (Vietnamese mobile, 10 digits, +84/spaces/dots/dashes normalised).
 */
export function phoneNumberProblem(raw: string): string {
  const value = raw.trim();
  if (!value) return 'Số điện thoại người nhận là bắt buộc';
  if (/[^\d\s.\-()+]/.test(value)) return 'Số điện thoại chỉ được gồm chữ số (có thể bắt đầu bằng +84).';
  const normalized = normalizePhoneNumber(value);
  if (!/^\d+$/.test(normalized)) return 'Số điện thoại chỉ được gồm chữ số (có thể bắt đầu bằng +84).';
  if (!normalized.startsWith('0')) return 'Số điện thoại phải bắt đầu bằng 0 hoặc +84.';
  if (normalized.length !== 10) return `Số điện thoại phải có đúng 10 chữ số (bạn đã nhập ${normalized.length}).`;
  if (!validatePhoneNumber(normalized)) return 'Đầu số không hợp lệ: số di động Việt Nam bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
  return '';
}

/** Show the phone problem while typing once it is certain: a wrong character, or 10 or more digits typed. */
function phoneProblemIsCertain(raw: string): boolean {
  const value = raw.trim();
  if (!value) return false;
  if (/[^\d\s.\-()+]/.test(value)) return true;
  return normalizePhoneNumber(value).replace(/\D/g, '').length >= 10;
}

export interface AddressModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (data: AddressCreateRequest | AddressUpdateRequest) => Promise<void>;
  initialAddress?: AddressResponse | null;
  profileDefaults?: { fullName?: string | null; phone?: string | null } | null;
  isFirstAddress?: boolean;
}

export const AddressModal: React.FC<AddressModalProps> = ({
  isOpen,
  onClose,
  onSave,
  initialAddress,
  profileDefaults, // Kept in interface for backward compatibility, ignored per FR-036 for new address
  isFirstAddress = false,
}) => {
  const [recipientName, setRecipientName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [provinceCode, setProvinceCode] = useState('');
  const [provinceName, setProvinceName] = useState('');
  const [communeCode, setCommuneCode] = useState('');
  const [communeName, setCommuneName] = useState('');
  const [communeFilter, setCommuneFilter] = useState('');
  const [detailAddress, setDetailAddress] = useState('');
  const [addressType, setAddressType] = useState<'HOME' | 'OFFICE'>('HOME');
  const [isDefault, setIsDefault] = useState(false);

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  // Fields the Member has left at least once: their problem is shown under the field straight away.
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const touch = (field: string) => setTouched(prev => (prev[field] ? prev : { ...prev, [field]: true }));
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const modalRef = useRef<HTMLDivElement>(null);
  const firstInputRef = useRef<HTMLInputElement>(null);

  const {
    provinces,
    communes,
    loadingProvinces,
    loadingCommunes,
  } = useAdministrativeDivisions(provinceCode);

  // Filter communes locally without diacritics / case-sensitivity (FR-041)
  const filteredCommunes = useMemo(() => {
    if (!communeFilter.trim()) return communes;
    const searchClean = removeDiacritics(communeFilter.trim());
    return communes.filter(c => removeDiacritics(c.name).includes(searchClean));
  }, [communes, communeFilter]);

  // Progressive validation flags per FR-037 & FR-040
  const isNameValid =
    recipientName.trim().length > 0 &&
    recipientName.length <= 255 &&
    !/[\u0000-\u001F\u007F-\u009F]/.test(recipientName);

  const isPhoneValid = isNameValid && validatePhoneNumber(phoneNumber);
  const isProvinceValid = isPhoneValid && Boolean(provinceCode);
  const isCommuneValid = isProvinceValid && Boolean(communeCode);
  const isDetailValid =
    isCommuneValid &&
    detailAddress.trim().length > 0 &&
    detailAddress.length <= 255 &&
    !/[\u0000-\u001F\u007F-\u009F]/.test(detailAddress);

  const isFormValid = isNameValid && isPhoneValid && isProvinceValid && isCommuneValid && isDetailValid;

  // Initialize or reset form values
  useEffect(() => {
    if (!isOpen) return;

    setFieldErrors({});
    setTouched({});
    setSubmitError(null);
    setCommuneFilter('');

    if (initialAddress) {
      setRecipientName(initialAddress.recipientName || '');
      setPhoneNumber(initialAddress.phoneNumber || '');
      setProvinceCode(initialAddress.provinceCode || initialAddress.provinceId || '');
      setProvinceName(initialAddress.provinceName || '');
      setCommuneCode(initialAddress.communeCode || initialAddress.wardId || '');
      setCommuneName(initialAddress.communeName || initialAddress.wardName || '');
      setDetailAddress(initialAddress.detailAddress || '');
      setAddressType(initialAddress.addressType || 'HOME');
      setIsDefault(Boolean(initialAddress.isDefault));
    } else {
      // New Address: FR-036 MUST open in clean state, NO profile prefill
      setRecipientName('');
      setPhoneNumber('');
      setProvinceCode('');
      setProvinceName('');
      setCommuneCode('');
      setCommuneName('');
      setDetailAddress('');
      setAddressType('HOME');
      setIsDefault(isFirstAddress);
    }

    // Auto-focus first input
    setTimeout(() => {
      firstInputRef.current?.focus();
    }, 50);
  }, [isOpen, initialAddress, isFirstAddress]);

  // Escape key listener to close modal
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !submitting) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, submitting, onClose]);

  if (!isOpen) return null;

  // FR-037: Changing a province clears the selected commune and detail address
  const handleProvinceChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const pCode = e.target.value;
    setProvinceCode(pCode);
    const found = provinces.find(p => (p.code || p.id) === pCode);
    setProvinceName(found ? found.name : '');
    setCommuneCode('');
    setCommuneName('');
    setDetailAddress('');
    setCommuneFilter('');
    if (fieldErrors.provinceCode || fieldErrors.provinceName) {
      setFieldErrors(prev => ({ ...prev, provinceCode: '', provinceName: '' }));
    }
  };

  // FR-037: Changing a commune clears the detail address
  const handleCommuneChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const cCode = e.target.value;
    setCommuneCode(cCode);
    const found = communes.find(c => (c.code || c.id) === cCode);
    setCommuneName(found ? found.name : '');
    setDetailAddress('');
    if (fieldErrors.communeCode || fieldErrors.communeName) {
      setFieldErrors(prev => ({ ...prev, communeCode: '', communeName: '' }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    const dataToValidate = {
      recipientName,
      phoneNumber,
      provinceCode,
      provinceName,
      communeCode,
      communeName,
      detailAddress,
    };

    const errors = validateAddressInput(dataToValidate);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    try {
      await onSave({
        recipientName: recipientName.trim(),
        phoneNumber: normalizePhoneNumber(phoneNumber),
        provinceCode,
        communeCode,
        detailAddress: detailAddress.trim(),
        addressType,
        isDefault: isFirstAddress ? true : isDefault,
        ...(isEditing && initialAddress?.version !== undefined ? { version: initialAddress.version } : {}),
        // Compatibility attributes
        provinceId: provinceCode,
        provinceName,
        districtId: communeCode,
        districtName: communeName,
        wardId: communeCode,
        wardName: communeName,
      });
      onClose();
    } catch (err: any) {
      setSubmitError(err?.message || 'Có lỗi xảy ra khi lưu địa chỉ. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const isEditing = Boolean(initialAddress);
  const nameError = fieldErrors.recipientName
    || (touched.recipientName && !recipientName.trim() ? 'Họ và tên người nhận là bắt buộc' : '');
  const phoneError = fieldErrors.phoneNumber
    || (touched.phoneNumber || phoneProblemIsCertain(phoneNumber) ? phoneNumberProblem(phoneNumber) : '');
  const detailError = fieldErrors.detailAddress
    || (touched.detailAddress && isCommuneValid && !detailAddress.trim()
      ? 'Địa chỉ chi tiết (số nhà, tên đường...) là bắt buộc' : '');
  const isDefaultFixed = isFirstAddress || (isEditing && Boolean(initialAddress?.isDefault));

  return (
    <div
      className="account-modal-overlay"
      role="presentation"
      onClick={e => {
        if (e.target === e.currentTarget && !submitting) onClose();
      }}
    >
      <div
        ref={modalRef}
        className="account-modal-container"
        role="dialog"
        aria-modal="true"
        aria-labelledby="address-modal-title"
      >
        <div className="account-modal-header">
          <h2 id="address-modal-title" className="account-modal-title">
            {isEditing ? 'Cập nhật địa chỉ nhận hàng' : 'Thêm địa chỉ nhận hàng mới'}
          </h2>
          <button
            type="button"
            className="account-modal-close-btn"
            onClick={onClose}
            aria-label="Đóng"
            disabled={submitting}
          >
            ✕
          </button>
        </div>

        {submitError && (
          <div className="account-alert account-alert-error" role="alert" style={{ margin: '1rem' }}>
            <span>{submitError}</span>
          </div>
        )}

        <form className="account-form account-modal-body" onSubmit={handleSubmit}>
          {/* Row 1: Progressive Step 1 (Name) & Step 2 (Phone) */}
          <div className="account-form-grid">
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-recipient-name">
                Họ và tên người nhận
              </label>
              <input
                ref={firstInputRef}
                id="modal-recipient-name"
                type="text"
                className="account-input"
                value={recipientName}
                onChange={e => {
                  setRecipientName(e.target.value);
                  if (fieldErrors.recipientName) setFieldErrors(prev => ({ ...prev, recipientName: '' }));
                }}
                onBlur={() => touch('recipientName')}
                maxLength={255}
                aria-invalid={Boolean(nameError)}
                disabled={submitting}
                placeholder="Ví dụ: Nguyễn Văn A"
              />
              {nameError && (
                <span className="account-field-error" role="alert">
                  {nameError}
                </span>
              )}
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-phone-number">
                Số điện thoại
              </label>
              <input
                id="modal-phone-number"
                type="tel"
                className="account-input"
                value={phoneNumber}
                onChange={e => {
                  setPhoneNumber(e.target.value);
                  if (fieldErrors.phoneNumber) setFieldErrors(prev => ({ ...prev, phoneNumber: '' }));
                }}
                onBlur={() => touch('phoneNumber')}
                maxLength={30}
                aria-invalid={Boolean(phoneError)}
                aria-describedby="modal-phone-number-message"
                disabled={submitting || !isNameValid}
                placeholder={!isNameValid ? '— Nhập Họ và tên trước —' : 'Ví dụ: 0912345678'}
              />
              {phoneError ? (
                <span id="modal-phone-number-message" className="account-field-error" role="alert">
                  {phoneError}
                </span>
              ) : (
                <span id="modal-phone-number-message" className="account-field-hint">
                  {!isNameValid
                    ? 'Vui lòng nhập Họ tên để mở khóa trường này.'
                    : 'Số di động 10 chữ số (tự chuẩn hóa +84/dấu cách).'}
                </span>
              )}
            </div>
          </div>

          {/* Row 2: Progressive Step 3 (Province) & Step 4 (Commune/Ward 2-level) */}
          <div className="account-form-grid">
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-province">
                Tỉnh / Thành phố
              </label>
              <select
                id="modal-province"
                className="account-select"
                value={provinceCode}
                onChange={handleProvinceChange}
                disabled={submitting || !isPhoneValid || loadingProvinces}
                aria-invalid={Boolean(fieldErrors.provinceCode || fieldErrors.provinceName)}
              >
                <option value="">
                  {loadingProvinces
                    ? 'Đang tải danh sách…'
                    : !isPhoneValid
                    ? '— Nhập Số điện thoại hợp lệ trước —'
                    : '— Chọn Tỉnh/Thành phố —'}
                </option>
                {provinces.map(p => {
                  const val = p.code || p.id;
                  return (
                    <option key={val} value={val}>
                      {p.name}
                    </option>
                  );
                })}
              </select>
              {(fieldErrors.provinceCode || fieldErrors.provinceName) && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.provinceCode || fieldErrors.provinceName}
                </span>
              )}
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-commune">
                Phường / Xã / Khu vực đặc thù
              </label>
              {communes.length > 5 && isProvinceValid && (
                <input
                  type="text"
                  className="account-input"
                  style={{ marginBottom: '0.375rem', fontSize: '0.85rem' }}
                  placeholder="🔍 Lọc nhanh phường, xã..."
                  value={communeFilter}
                  onChange={e => setCommuneFilter(e.target.value)}
                  disabled={submitting || !isProvinceValid || loadingCommunes}
                  aria-label="Lọc nhanh Phường / Xã"
                />
              )}
              <select
                id="modal-commune"
                className="account-select"
                value={communeCode}
                onChange={handleCommuneChange}
                disabled={submitting || !isProvinceValid || loadingCommunes}
                aria-invalid={Boolean(fieldErrors.communeCode || fieldErrors.communeName)}
              >
                <option value="">
                  {loadingCommunes
                    ? 'Đang tải danh sách phường/xã…'
                    : !isProvinceValid
                    ? '— Chọn Tỉnh/Thành trước —'
                    : '— Chọn Phường/Xã/Khu vực đặc thù —'}
                </option>
                {filteredCommunes.map(c => {
                  const val = c.code || c.id;
                  return (
                    <option key={val} value={val}>
                      {c.name}
                    </option>
                  );
                })}
              </select>
              {(fieldErrors.communeCode || fieldErrors.communeName) && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.communeCode || fieldErrors.communeName}
                </span>
              )}
            </div>
          </div>

          {/* Row 3: Progressive Step 5: Detail Address */}
          <div className="account-field">
            <label className="account-label account-label-required" htmlFor="modal-detail-address">
              Địa chỉ cụ thể (Số nhà, Tòa nhà, Tên đường)
            </label>
            <input
              id="modal-detail-address"
              type="text"
              className="account-input"
              value={detailAddress}
              onChange={e => {
                setDetailAddress(e.target.value);
                if (fieldErrors.detailAddress) setFieldErrors(prev => ({ ...prev, detailAddress: '' }));
              }}
              onBlur={() => touch('detailAddress')}
              maxLength={255}
              aria-invalid={Boolean(detailError)}
              disabled={submitting || !isCommuneValid}
              placeholder={!isCommuneValid ? '— Vui lòng chọn Phường/Xã trước —' : 'Ví dụ: Số 25, Ngõ 12 Đội Cấn...'}
            />
            {detailError && (
              <span className="account-field-error" role="alert">
                {detailError}
              </span>
            )}
          </div>

          {/* Row 4: Address Type */}
          <div className="account-field">
            <span className="account-label">Loại địa chỉ</span>
            <div style={{ display: 'flex', gap: '1.5rem', marginTop: '0.25rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="addressType"
                  value="HOME"
                  checked={addressType === 'HOME'}
                  onChange={() => setAddressType('HOME')}
                  disabled={submitting}
                />
                <span>Nhà riêng</span>
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="addressType"
                  value="OFFICE"
                  checked={addressType === 'OFFICE'}
                  onChange={() => setAddressType('OFFICE')}
                  disabled={submitting}
                />
                <span>Văn phòng</span>
              </label>
            </div>
          </div>

          {/* Row 5: Default Address Checkbox */}
          <div className="account-field" style={{ marginTop: '0.5rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.625rem', cursor: isDefaultFixed ? 'not-allowed' : 'pointer' }}>
              <input
                type="checkbox"
                checked={isDefault}
                onChange={e => {
                  if (!isDefaultFixed) setIsDefault(e.target.checked);
                }}
                disabled={submitting || isDefaultFixed}
              />
              <span style={{ fontSize: '0.925rem', color: isDefaultFixed ? '#6b7280' : '#111827' }}>
                Đặt làm địa chỉ giao hàng mặc định
                {isFirstAddress && ' (Địa chỉ đầu tiên tự động đặt làm mặc định)'}
                {!isFirstAddress && isEditing && initialAddress?.isDefault && ' (Đây là địa chỉ mặc định hiện tại)'}
              </span>
            </label>
          </div>

          <div className="account-modal-footer">
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="account-btn account-btn-primary"
              disabled={submitting || !isFormValid}
            >
              {submitting ? 'Đang lưu…' : isEditing ? 'Cập nhật' : 'Hoàn thành'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AddressModal;
