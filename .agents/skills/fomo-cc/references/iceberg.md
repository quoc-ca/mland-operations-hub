# The hidden part of the iceberg

A checklist of what vibe coders don't see when they look at a product. Pick the 5-8 items **most specific to the product the user named** and rewrite them concretely in the user's language - don't paste this list.

## Engineering

- **Scale** - localhost with 1 user is nothing like 1 million concurrent users. Databases, caching, queues, sharding, CDN
- **Real-time** - chat, notifications, driver location: websockets, pub/sub, reconnects, message ordering
- **Feed / ranking / recommendations** - not `ORDER BY created_at`. It's ML, A/B tests, years of behavioral data
- **Fan-out** - an account with millions of followers posts, and it has to land in millions of timelines within seconds
- **Data consistency** - two people buying the last item, double charges, lost orders
- **Media** - upload, compression, video transcoding, petabytes of storage, bandwidth bills
- **Search** - full-text, language-specific quirks (diacritics, CJK tokenization), typos, ranking
- **Mobile** - iOS + Android, App Store / Play Store review, old versions still in the wild, push notifications
- **Observability** - logs, metrics, alerts. Without them, it's down and you don't even know
- **Backup and recovery** - lose user data once and your credibility is gone

## Security and safety

- **Auth** - login, password reset, 2FA, OAuth, sessions, brute-force protection
- **Security** - SQL injection, XSS, API keys leaked in AI-generated code, broken access control letting user A see user B's data
- **Spam and bots** - launch today, mass bot sign-ups tomorrow
- **Content moderation** - scams, violence, child abuse material, misinformation. Legal liability included
- **Fraud** - fake orders, fake reviews, promo abuse, chargebacks

## Legal and money (pick by the TARGET MARKET, not the user's language)

- **Payments** - payment gateway, reconciliation, refunds, PCI DSS; e-wallets and stored value usually need a license
- **Personal data**
  - Vietnam: Decree 13/2023 and the Personal Data Protection Law
  - EU/EEA: GDPR
  - Japan: APPI (Act on the Protection of Personal Information)
  - US: state privacy laws (e.g. CCPA/CPRA in California), COPPA if children may use it
  - Elsewhere: assume a data-protection law exists and say so generically
- **Platform rules**
  - Vietnam: social networks need a license; Decree 147/2024 (effective 25 Dec 2024) requires accounts to be verified by phone number before posting
  - EU: Digital Services Act obligations for online platforms (notice-and-action, transparency)
  - Japan: Telecommunications Business Act notification for messaging-type services
- **Sector licenses** - transport, finance, healthcare, food delivery each have their own rules in every market
- **Trademarks and copyright** - clone someone's UI and brand name, then wait for the lawyer's letter
- **Infrastructure cost** - vibe code it, deploy to the cloud, get a bill bigger than your revenue at month end

## Business and operations

- **Cold start / network effects** - a social network without your friends is empty; a marketplace without sellers has no buyers and vice versa
- **Distribution** - building is easy, getting users is hard. "Build it and they will come" is a fairy tale
- **Customer support** - who answers when a user loses money at 11 p.m.?
- **On-call** - the system dies at 3 a.m., AI doesn't wake up for you
- **Maintenance** - AI-generated code you don't understand means you can't fix it when it breaks. Tech debt compounds
- **Offline operations** - drivers, warehouses, couriers, sellers: real humans, not promptable
- **Fundraising** - seed investors fund teams and traction, not demos. "Non-technical founder, no engineers, AI does it all" sounds like "nobody can fix it when it breaks"
