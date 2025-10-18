\# 📚 Ecommerce AG Books — Fullstack



Full-stack services for the \*\*AG Books\*\* online store.



---



\## 🎯 Feature Overview



\### 💳 Payments

\- \*\*PIX (Efí Bank)\*\* with QR Code, instant confirmation.

\- \*\*Automatic Payout\*\* to authors via PIX with ~\*\*1s latency\*\*.

\- \*\*Credit Cards\*\* with tokenization + installments.

\- \*\*Webhooks\*\* for real-time order orchestration.



\### 🎫 Coupons

\- Real-time validation (API-first) with fallback.

\- Configurable discount caps (max \*\*R$ 15,00\*\*).

\- State persistence across cart → checkout → payment.



\### 🛒 Shopping

\- Real-time cart with cookie persistence.

\- Live stock checks \& limits.

\- Dynamic shipping by CEP + weight.

\- End-to-end order tracking.



\### 📧 Notifications

\- Buyer confirmation, seller notification, and author payout alerts (no templates here).



---



\## 🚀 Tech Stack



\*\*Architecture\*\*

\- Clean Code, refactoring best practices, REST

\- Microservices-friendly design



\*\*Frontend\*\*

\- React 18 + TypeScript

\- React Router, Custom Hooks

\- Session Storage, GA4

\- Tailwind CSS (responsive)



\*\*Backend\*\*

\- Kotlin + Spring Boot, Gradle

\- PostgreSQL + Hibernate

\- JavaMail (SMTP)

\- Efí Bank (PIX + Card)

\- Webhooks, Heroku



\*\*Payment Gateway\*\*

\- PIX (instant)

\- Card tokenization + installments

\- Real-time confirmation



\*\*Services\*\*

\- `PixCheckoutService` — PIX processing

\- `CardCheckoutService` — card handling

\- `CouponService` — validation/discounts

\- `EmailService` — notifications

\- `WebhookService` — payment confirmations



---



\## 📊 API



\*\*Swagger UI:\*\*  

https://ecommerceag-6fa0e6a5edbf.herokuapp.com/swagger-ui/index.html



\*\*Key Endpoints\*\*

\- `POST /api/checkout/pix` — PIX payment

\- `POST /api/checkout/card` — Card payment

\- `POST /api/coupons/validate` — Coupon validation

\- `GET /api/books` — Catalog

\- `GET /api/stock` — Stock info



---



\## 🔒 Security



\- Secrets via environment variables

\- Input validation

\- Payment tokenization

\- CORS protection

\- Graceful error handling



---



\## 🚢 Deployment



\- \*\*Frontend:\*\* Vercel  

\- \*\*Backend:\*\* Heroku  

\- \*\*Database:\*\* Heroku PostgreSQL  

\- \*\*Email:\*\* SMTP (JavaMail)  

\- \*\*Monitoring:\*\* App logs + error tracking



---



\## 🧭 UX Flow



1\. Browse books → Add to cart  

2\. Apply coupon → Real-time validation  

3\. Checkout → Form + shipping calc  

4\. Pay → PIX QR or Credit Card  

5\. Confirmation → Notifications  

6\. Track order → Full lifecycle



\*\*Highlights\*\*

\- Real-time updates (cart + stock)

\- Persistent state (cart/coupons)

\- Mobile-first, fast loading

\- Robust error recovery



---



\## 📮 Contact



\- Email: <luhmgasparetto@gmail.com>  

\- WhatsApp: +55 71 99410-5740



