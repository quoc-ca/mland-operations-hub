import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { addressApiClient } from '../api/AddressApiClient';
import AddressModal, { phoneNumberProblem } from '../components/AddressModal';

describe('Address phone number shows its problem under the field', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(addressApiClient, 'listProvinces').mockResolvedValue([]);
    vi.spyOn(addressApiClient, 'listCommunes').mockResolvedValue([]);
  });

  it('explains each kind of wrong number', () => {
    expect(phoneNumberProblem('')).toBe('Số điện thoại người nhận là bắt buộc');
    expect(phoneNumberProblem('09a2345678')).toMatch(/chỉ được gồm chữ số/);
    expect(phoneNumberProblem('912345678')).toMatch(/bắt đầu bằng 0 hoặc \+84/);
    expect(phoneNumberProblem('091234567')).toBe('Số điện thoại phải có đúng 10 chữ số (bạn đã nhập 9).');
    expect(phoneNumberProblem('09123456789')).toBe('Số điện thoại phải có đúng 10 chữ số (bạn đã nhập 11).');
    expect(phoneNumberProblem('0212345678')).toMatch(/Đầu số không hợp lệ/);
    expect(phoneNumberProblem('0912345678')).toBe('');
    expect(phoneNumberProblem('+84 912 345 678')).toBe('');
  });

  function openForm() {
    render(<AddressModal isOpen={true} onClose={vi.fn()} onSave={vi.fn()} isFirstAddress={false} />);
    fireEvent.change(screen.getByLabelText(/Họ và tên người nhận/i), { target: { value: 'Nguyễn Văn A' } });
    return screen.getByLabelText(/Số điện thoại/i) as HTMLInputElement;
  }

  it('shows the error right away for a letter or a number that is already too long', () => {
    const phone = openForm();
    fireEvent.change(phone, { target: { value: '09a' } });
    expect(screen.getByRole('alert')).toHaveTextContent('chỉ được gồm chữ số');
    fireEvent.change(phone, { target: { value: '0212345678' } });
    expect(screen.getByRole('alert')).toHaveTextContent('Đầu số không hợp lệ');
    fireEvent.change(phone, { target: { value: '0912345678' } });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByText('Số di động 10 chữ số (tự chuẩn hóa +84/dấu cách).')).toBeInTheDocument();
  });

  it('waits until the field is left before saying a short number is too short', () => {
    const phone = openForm();
    fireEvent.change(phone, { target: { value: '0912' } });
    expect(screen.queryByRole('alert')).toBeNull();
    fireEvent.blur(phone);
    expect(screen.getByRole('alert')).toHaveTextContent('phải có đúng 10 chữ số (bạn đã nhập 4)');
    expect(phone).toHaveAttribute('aria-invalid', 'true');
  });

  it('says the name is required after leaving it empty', () => {
    render(<AddressModal isOpen={true} onClose={vi.fn()} onSave={vi.fn()} isFirstAddress={false} />);
    fireEvent.blur(screen.getByLabelText(/Họ và tên người nhận/i));
    expect(screen.getByRole('alert')).toHaveTextContent('Họ và tên người nhận là bắt buộc');
  });
});
