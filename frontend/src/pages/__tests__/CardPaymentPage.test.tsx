import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import CardPaymentPage from '../CardPaymentPage';
import { CartContext } from '../../context/CartContext';
import * as efiCard from '../../services/efiCard';
import * as http from '../../api/http';
import * as cookieUtils from '../../utils/cookieUtils';

// Mock modules
vi.mock('../../services/efiCard');
vi.mock('../../api/http');
vi.mock('../../analytics', () => ({
    analytics: {
        addPaymentInfo: vi.fn(),
    },
    mapCartItems: vi.fn((items) => items),
}));

// Mock navigation
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

describe('CardPaymentPage', () => {
    const mockCart = [
        {
            id: '1',
            title: 'Livro Teste',
            price: 50.0,
            quantity: 2,
            imageUrl: '/test-image.jpg',
            category: 'Ficção',
        },
    ];

    const mockForm = {
        name: 'João Silva',
        cpf: '123.456.789-00',
        email: 'joao@test.com',
        phone: '11999999999',
        cep: '01310-100',
        street: 'Av Paulista',
        number: '1000',
        complement: 'Apto 101',
        neighborhood: 'Bela Vista',
        city: 'São Paulo',
        state: 'SP',
        shipping: 15.0,
    };

    const mockCartContext = {
        cartItems: mockCart,
        totalPrice: 100,
        clearCart: vi.fn(),
        addToCart: vi.fn(),
        removeFromCart: vi.fn(),
        updateQuantity: vi.fn(),
    };

    beforeEach(() => {
        // Set environment variables
        vi.stubEnv('VITE_EFI_PAYEE_CODE', 'TEST_PAYEE_CODE');
        vi.stubEnv('VITE_EFI_SANDBOX', 'true');

        // Mock cookie storage
        vi.spyOn(cookieUtils.cookieStorage, 'get').mockImplementation((key) => {
            if (key === 'cart') return mockCart;
            if (key === 'checkoutForm') return mockForm;
            return null;
        });
        vi.spyOn(cookieUtils.cookieStorage, 'remove').mockImplementation(() => { });

        // Mock EFI card service
        vi.mocked(efiCard.isScriptBlocked).mockResolvedValue(false);
        vi.mocked(efiCard.verifyBrandFromNumber).mockResolvedValue('visa');
        vi.mocked(efiCard.tokenize).mockResolvedValue({
            payment_token: 'mock_token_123',
            card_mask: '411111******1111',
        });

        // Clear all mocks
        mockNavigate.mockClear();
        mockCartContext.clearCart.mockClear();
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.unstubAllEnvs();
    });

    const renderComponent = (contextValue = mockCartContext) => {
        return render(
            <BrowserRouter>
                <CartContext.Provider value={contextValue}>
                    <CardPaymentPage />
                </CartContext.Provider>
            </BrowserRouter>
        );
    };

    describe('Component Rendering', () => {
        it('should render the payment form correctly', () => {
            renderComponent();

            expect(screen.getByText('Pagamento com Cartão')).toBeInTheDocument();
            expect(screen.getByPlaceholderText(/número do cartão/i)).toBeInTheDocument();
            expect(screen.getByPlaceholderText(/nome impresso/i)).toBeInTheDocument();
            expect(screen.getByPlaceholderText(/mm/i)).toBeInTheDocument();
            expect(screen.getByPlaceholderText(/aaaa/i)).toBeInTheDocument();
            expect(screen.getByPlaceholderText(/cvv/i)).toBeInTheDocument();
        });

        it('should display order summary with correct totals', () => {
            renderComponent();

            // Subtotal: 2 * 50 = 100
            expect(screen.getByText(/subtotal/i)).toBeInTheDocument();
            expect(screen.getByText('R$ 100,00')).toBeInTheDocument();

            // Shipping
            expect(screen.getByText(/frete/i)).toBeInTheDocument();
            expect(screen.getByText('R$ 15,00')).toBeInTheDocument();

            // Total: 100 + 15 = 115
            expect(screen.getByText(/total/i)).toBeInTheDocument();
            expect(screen.getByText('R$ 115,00')).toBeInTheDocument();
        });

        it('should render payment button', () => {
            renderComponent();

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            expect(payButton).toBeInTheDocument();
        });
    });

    describe('Form Validation', () => {
        it('should disable payment button when form is invalid', () => {
            renderComponent();

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            expect(payButton).toBeDisabled();
        });

        it('should enable payment button when form is valid', async () => {
            renderComponent();
            const user = userEvent.setup();

            // Fill in card details
            const cardNumberInput = screen.getByPlaceholderText(/número do cartão/i);
            const holderNameInput = screen.getByPlaceholderText(/nome impresso/i);
            const monthInput = screen.getByPlaceholderText(/mm/i);
            const yearInput = screen.getByPlaceholderText(/aaaa/i);
            const cvvInput = screen.getByPlaceholderText(/cvv/i);

            await user.type(cardNumberInput, '4111111111111111');
            await user.type(holderNameInput, 'JOAO SILVA');
            await user.type(monthInput, '12');
            await user.type(yearInput, '2025');
            await user.type(cvvInput, '123');

            await waitFor(() => {
                const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
                expect(payButton).not.toBeDisabled();
            });
        });

        it('should format card number with spaces', async () => {
            renderComponent();
            const user = userEvent.setup();

            const cardNumberInput = screen.getByPlaceholderText(/número do cartão/i) as HTMLInputElement;
            await user.type(cardNumberInput, '4111111111111111');

            await waitFor(() => {
                expect(cardNumberInput.value).toMatch(/\d{4}\s\d{4}\s\d{4}\s\d{4}/);
            });
        });
    });

    describe('Card Brand Detection', () => {
        it('should auto-detect Visa from card number', async () => {
            renderComponent();
            const user = userEvent.setup();

            const cardNumberInput = screen.getByPlaceholderText(/número do cartão/i);
            await user.type(cardNumberInput, '411111');

            await waitFor(() => {
                expect(efiCard.verifyBrandFromNumber).toHaveBeenCalled();
            });
        });

        it('should update CVV length based on card brand', async () => {
            renderComponent();
            const user = userEvent.setup();

            const cvvInput = screen.getByPlaceholderText(/cvv/i) as HTMLInputElement;

            // Visa/Mastercard should have 3 digits
            await user.type(cvvInput, '1234');
            await waitFor(() => {
                expect(cvvInput.value.length).toBeLessThanOrEqual(3);
            });
        });
    });

    describe('Payment Flow', () => {
        it('should handle successful payment', async () => {
            const mockResponse = {
                success: true,
                message: 'Pagamento aprovado',
                orderId: '12345',
                chargeId: 'charge_123',
                status: 'PAID',
            };

            vi.mocked(http.apiPost).mockResolvedValue(mockResponse);

            renderComponent();
            const user = userEvent.setup();

            // Fill in valid card details
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(efiCard.tokenize).toHaveBeenCalled();
                expect(http.apiPost).toHaveBeenCalledWith('/checkout/card', expect.any(Object));
            });

            await waitFor(() => {
                expect(mockCartContext.clearCart).toHaveBeenCalled();
                expect(mockNavigate).toHaveBeenCalledWith(
                    expect.stringContaining('/pedido-confirmado')
                );
            });
        });

        it('should send correct payload to backend', async () => {
            const mockResponse = {
                success: true,
                orderId: '12345',
                status: 'PAID',
            };

            vi.mocked(http.apiPost).mockResolvedValue(mockResponse);

            renderComponent();
            const user = userEvent.setup();

            // Fill in card details
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(http.apiPost).toHaveBeenCalledWith(
                    '/checkout/card',
                    expect.objectContaining({
                        payment: 'card',
                        paymentToken: 'mock_token_123',
                        cartItems: mockCart,
                        total: 115,
                        shipping: 15,
                    })
                );
            });
        });
    });

    describe('Error Handling', () => {
        it('should display error message from API', async () => {
            const mockError = {
                response: {
                    data: {
                        code: 'PAYMENT_GATEWAY_ERROR',
                        message: 'Erro ao processar pagamento',
                    },
                },
            };

            vi.mocked(http.apiPost).mockRejectedValue(mockError);

            renderComponent();
            const user = userEvent.setup();

            // Fill in card details
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(screen.getByText(/erro ao processar pagamento/i)).toBeInTheDocument();
            });
        });

        it('should handle tokenization errors', async () => {
            vi.mocked(efiCard.tokenize).mockRejectedValue(new Error('Tokenization failed'));

            renderComponent();
            const user = userEvent.setup();

            // Fill in card details
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(screen.getByText(/tokenization failed/i)).toBeInTheDocument();
            });
        });

        it('should handle out of stock errors', async () => {
            const mockError = {
                response: {
                    data: {
                        code: 'OUT_OF_STOCK',
                        message: 'Produto indisponível',
                    },
                },
            };

            vi.mocked(http.apiPost).mockRejectedValue(mockError);

            renderComponent();
            const user = userEvent.setup();

            // Fill in card details
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(screen.getByText(/produto indisponível/i)).toBeInTheDocument();
            });
        });
    });

    describe('Timer Functionality', () => {
        it('should show warning when time is running low', async () => {
            const mockResponse = {
                success: false,
                orderId: '12345',
                status: 'PENDING',
                ttlSeconds: 50,
                warningAt: 60,
                reserveExpiresAt: new Date(Date.now() + 50000).toISOString(),
            };

            vi.mocked(http.apiPost).mockResolvedValue(mockResponse);

            renderComponent();
            const user = userEvent.setup();

            // Fill and submit form
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            // Timer warning should appear
            await waitFor(
                () => {
                    expect(screen.getByText(/tempo restante/i)).toBeInTheDocument();
                },
                { timeout: 10000 }
            );
        });
    });

    describe('Redirect on Invalid Cart', () => {
        it('should redirect to checkout if cart is empty', () => {
            const emptyCartContext = {
                ...mockCartContext,
                cartItems: [],
            };

            renderComponent(emptyCartContext);

            expect(mockNavigate).toHaveBeenCalledWith('/checkout');
        });

        it('should not redirect if payment was successful', async () => {
            const mockResponse = {
                success: true,
                orderId: '12345',
                status: 'PAID',
            };

            vi.mocked(http.apiPost).mockResolvedValue(mockResponse);

            renderComponent();
            const user = userEvent.setup();

            // Complete payment
            await user.type(screen.getByPlaceholderText(/número do cartão/i), '4111111111111111');
            await user.type(screen.getByPlaceholderText(/nome impresso/i), 'JOAO SILVA');
            await user.type(screen.getByPlaceholderText(/mm/i), '12');
            await user.type(screen.getByPlaceholderText(/aaaa/i), '2025');
            await user.type(screen.getByPlaceholderText(/cvv/i), '123');

            const payButton = screen.getByRole('button', { name: /pagar com cartão/i });
            await user.click(payButton);

            await waitFor(() => {
                expect(mockNavigate).toHaveBeenCalledWith(
                    expect.stringContaining('/pedido-confirmado')
                );
            });

            // Should not redirect to /checkout even though cart is cleared
            expect(mockNavigate).not.toHaveBeenCalledWith('/checkout');
        });
    });
});
