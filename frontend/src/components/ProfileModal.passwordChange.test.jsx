/**
 * ProfileModal — password change validation by role
 *
 * Root cause fixed: HOSPITAL_ADMIN phone was seeded as "" at creation.
 * validate() required phone for all non-SUPER_ADMIN roles, so admin's
 * password-change always failed with "Please fix the validation errors in the form."
 * Fix: phone is optional (not required) for HOSPITAL_ADMIN.
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const toastError = vi.fn();
const toastSuccess = vi.fn();

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError }),
  ToastProvider: ({ children }) => children,
}));

vi.mock('../hooks/useWebSocket', () => ({ default: () => ({ lastMessage: null }) }));

const updateProfile = vi.fn().mockResolvedValue({});

vi.mock('../services/authService', () => ({
  default: {
    getCurrentUser: () => ({ role: 'HOSPITAL_ADMIN', isSingleDoctor: false }),
    getProfile: vi.fn().mockResolvedValue({
      name: 'Admin User',
      email: 'admin@hospital.com',
      role: 'HOSPITAL_ADMIN',
      phone: '', // ← seeded empty at hospital creation
      age: null,
      gender: null,
      specialization: null,
      hospitalName: 'Test Hospital',
      hospitalAddress: '',
      hospitalPhone: '',
      parentOrganization: '',
      logoUrl: '',
    }),
    updateProfile,
    updateCurrentUser: vi.fn(),
  },
}));

const renderModal = async () => {
  const { default: ProfileModal } = await import('./ProfileModal');
  render(<ProfileModal isOpen onClose={vi.fn()} />);
  // Wait for profile to load
  await waitFor(() =>
    expect(screen.queryByText('Fetching fresh profile details...')).not.toBeInTheDocument()
  );
};

const enterEditMode = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Edit Profile/i }));
};

const openPasswordSection = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Change Password/i }));
};

describe('ProfileModal — HOSPITAL_ADMIN password change with empty phone', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does NOT show a toast error when admin changes password with empty phone', async () => {
    await renderModal();
    await enterEditMode();
    await openPasswordSection();

    fireEvent.change(screen.getByPlaceholderText('Verify current password'), {
      target: { value: 'OldPass1' },
    });
    fireEvent.change(screen.getByPlaceholderText('Minimum 6 characters'), {
      target: { value: 'NewPass1' },
    });
    fireEvent.change(screen.getByPlaceholderText('Repeat new password'), {
      target: { value: 'NewPass1' },
    });

    // Click Save
    fireEvent.click(screen.getByRole('button', { name: /Save Settings/i }));

    await waitFor(() => {
      // Should NOT show form-invalid toast
      expect(toastError).not.toHaveBeenCalledWith('Please fix the validation errors in the form.');
    });

    // updateProfile should be called
    expect(updateProfile).toHaveBeenCalled();
  });

  it('shows error when admin enters invalid phone format', async () => {
    await renderModal();
    await enterEditMode();

    // Enter a bad phone
    fireEvent.change(screen.getByPlaceholderText('e.g. +1 555-0199'), {
      target: { value: 'not-a-phone' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Save Settings/i }));

    await waitFor(() => {
      expect(toastError).toHaveBeenCalledWith('Please fix the validation errors in the form.');
    });
    expect(updateProfile).not.toHaveBeenCalled();
  });

  it('phone field label has no required asterisk for HOSPITAL_ADMIN', async () => {
    await renderModal();
    await enterEditMode();
    // The label text should just be "Phone Number" without "*" meaning asterisk span is absent
    const phoneLabel =
      screen.getByText(/Phone Number/i).closest('label') ?? screen.getAllByText(/Phone Number/i)[0];
    expect(phoneLabel.querySelector('span.text-red-500')).toBeNull();
  });
});
