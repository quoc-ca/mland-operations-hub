import React, { useState, useEffect, useRef } from 'react';
import {
  AddressResponse,
  AddressCreateRequest,
  AddressUpdateRequest,
} from '../api/AddressApiClient';
import {
  validateAddressInput,
  normalizePhoneNumber,
  useAdministrativeDivisions,
} from '../hooks/useMemberAddresses';
import '../accountmanagement.css';

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
  profileDefaults,
  isFirstAddress = false,
}) => {
  const [recipientName, setRecipientName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [provinceId, setProvinceId] = useState('');
  const [provinceName, setProvinceName] = useState('');
  const [districtId, setDistrictId] = useState('');
  const [districtName, setDistrictName] = useState('');
  const [wardId, setWardId] = useState('');
  const [wardName, setWardName] = useState('');
  const [hamletName, setHamletName] = useState('');
  const [detailAddress, setDetailAddress] = useState('');
  const [addressType, setAddressType] = useState<'HOME' | 'OFFICE'>('HOME');
  const [isDefault, setIsDefault] = useState(false);

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const modalRef = useRef<HTMLDivElement>(null);
  const firstInputRef = useRef<HTMLInputElement>(null);

  const {
    provinces,
    districts,
    wards,
    loadingProvinces,
    loadingDistricts,
    loadingWards,
  } = useAdministrativeDivisions(provinceId, districtId);

  // Initialize or reset form values
  useEffect(() => {
    if (!isOpen) return;

    setFieldErrors({});
    setSubmitError(null);

    if (initialAddress) {
      setRecipientName(initialAddress.recipientName || '');
      setPhoneNumber(initialAddress.phoneNumber || '');
      setProvinceId(initialAddress.provinceId || '');
      setProvinceName(initialAddress.provinceName || '');
      setDistrictId(initialAddress.districtId || '');
      setDistrictName(initialAddress.districtName || '');
      setWardId(initialAddress.wardId || '');
      setWardName(initialAddress.wardName || '');
      setHamletName(initialAddress.hamletName || '');
      setDetailAddress(initialAddress.detailAddress || '');
      setAddressType(initialAddress.addressType || 'HOME');
      setIsDefault(Boolean(initialAddress.isDefault));
    } else {
      // New Address
      if (isFirstAddress && profileDefaults) {
        setRecipientName(profileDefaults.fullName || '');
        setPhoneNumber(profileDefaults.phone ? normalizePhoneNumber(profileDefaults.phone) : '');
      } else {
        setRecipientName('');
        setPhoneNumber('');
      }
      setProvinceId('');
      setProvinceName('');
      setDistrictId('');
      setDistrictName('');
      setWardId('');
      setWardName('');
      setHamletName('');
      setDetailAddress('');
      setAddressType('HOME');
      setIsDefault(isFirstAddress);
    }

    // Auto-focus first input
    setTimeout(() => {
      firstInputRef.current?.focus();
    }, 50);
  }, [isOpen, initialAddress, profileDefaults, isFirstAddress]);

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

  const handleProvinceChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const pId = e.target.value;
    setProvinceId(pId);
    const found = provinces.find(p => p.id === pId);
    setProvinceName(found ? found.name : '');
    // Reset subordinate divisions
    setDistrictId('');
    setDistrictName('');
    setWardId('');
    setWardName('');
    if (fieldErrors.provinceName) {
      setFieldErrors(prev => ({ ...prev, provinceName: '' }));
    }
  };

  const handleDistrictChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const dId = e.target.value;
    setDistrictId(dId);
    const found = districts.find(d => d.id === dId);
    setDistrictName(found ? found.name : '');
    // Reset subordinate ward
    setWardId('');
    setWardName('');
    if (fieldErrors.districtName) {
      setFieldErrors(prev => ({ ...prev, districtName: '' }));
    }
  };

  const handleWardChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const wId = e.target.value;
    setWardId(wId);
    const found = wards.find(w => w.id === wId);
    setWardName(found ? found.name : '');
    if (fieldErrors.wardName) {
      setFieldErrors(prev => ({ ...prev, wardName: '' }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    const dataToValidate = {
      recipientName,
      phoneNumber,
      provinceName,
      districtName,
      wardName,
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
        provinceId: provinceId || null,
        provinceName,
        districtId: districtId || null,
        districtName,
        wardId: wardId || null,
        wardName,
        hamletId: null,
        hamletName: hamletName.trim() || null,
        detailAddress: detailAddress.trim(),
        addressType,
        isDefault: isFirstAddress ? true : isDefault,
      });
      onClose();
    } catch (err: any) {
      setSubmitError(err?.message || 'Có lỗi xảy ra khi lưu địa chỉ. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const isEditing = Boolean(initialAddress);
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
          {/* Row 1: Name and Phone */}
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
                maxLength={255}
                aria-invalid={Boolean(fieldErrors.recipientName)}
                disabled={submitting}
                placeholder="Ví dụ: Nguyễn Văn A"
              />
              {fieldErrors.recipientName && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.recipientName}
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
                maxLength={30}
                aria-invalid={Boolean(fieldErrors.phoneNumber)}
                disabled={submitting}
                placeholder="Ví dụ: 0912345678"
              />
              {fieldErrors.phoneNumber && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.phoneNumber}
                </span>
              )}
              <span className="account-field-hint">Số di động 10 chữ số (tự chuẩn hóa +84/dấu cách).</span>
            </div>
          </div>

          {/* Row 2: Cascading Dropdowns: Province, District, Ward */}
          <div className="account-form-grid-3">
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-province">
                Tỉnh / Thành phố
              </label>
              <select
                id="modal-province"
                className="account-select"
                value={provinceId}
                onChange={handleProvinceChange}
                disabled={submitting || loadingProvinces}
                aria-invalid={Boolean(fieldErrors.provinceName)}
              >
                <option value="">
                  {loadingProvinces ? 'Đang tải danh sách…' : '— Chọn Tỉnh/Thành phố —'}
                </option>
                {provinces.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              {fieldErrors.provinceName && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.provinceName}
                </span>
              )}
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-district">
                Quận / Huyện
              </label>
              <select
                id="modal-district"
                className="account-select"
                value={districtId}
                onChange={handleDistrictChange}
                disabled={submitting || !provinceId || loadingDistricts}
                aria-invalid={Boolean(fieldErrors.districtName)}
              >
                <option value="">
                  {loadingDistricts
                    ? 'Đang tải…'
                    : !provinceId
                    ? '— Chọn Tỉnh/Thành trước —'
                    : '— Chọn Quận/Huyện —'}
                </option>
                {districts.map(d => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
              {fieldErrors.districtName && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.districtName}
                </span>
              )}
            </div>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="modal-ward">
                Phường / Xã
              </label>
              <select
                id="modal-ward"
                className="account-select"
                value={wardId}
                onChange={handleWardChange}
                disabled={submitting || !districtId || loadingWards}
                aria-invalid={Boolean(fieldErrors.wardName)}
              >
                <option value="">
                  {loadingWards
                    ? 'Đang tải…'
                    : !districtId
                    ? '— Chọn Quận/Huyện trước —'
                    : '— Chọn Phường/Xã —'}
                </option>
                {wards.map(w => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
              {fieldErrors.wardName && (
                <span className="account-field-error" role="alert">
                  {fieldErrors.wardName}
                </span>
              )}
            </div>
          </div>

          {/* Row 3: Optional Hamlet and Detail Address */}
          <div className="account-field">
            <label className="account-label" htmlFor="modal-hamlet">
              Thôn / Ấp / Xóm / Bản <span style={{ fontWeight: 'normal', color: '#6b7280' }}>(Không bắt buộc, GHTK Cấp 4)</span>
            </label>
            <input
              id="modal-hamlet"
              type="text"
              className="account-input"
              value={hamletName}
              onChange={e => setHamletName(e.target.value)}
              maxLength={128}
              disabled={submitting}
              placeholder="Ví dụ: Thôn 3, Xóm Cầu..."
            />
          </div>

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
              maxLength={255}
              aria-invalid={Boolean(fieldErrors.detailAddress)}
              disabled={submitting}
              placeholder="Ví dụ: Số 25, Ngõ 12 Đội Cấn..."
            />
            {fieldErrors.detailAddress && (
              <span className="account-field-error" role="alert">
                {fieldErrors.detailAddress}
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
              disabled={submitting}
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

