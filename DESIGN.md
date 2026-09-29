---

version: "alpha"

name: "Mland UI System"

# Điền tên design system hoặc tên project.

# Ví dụ: "Workshop Management System"

description: "A bilingual design system for Mland customer booking and commerce journeys, plus operational interfaces for Staff, Owner and Admin Technical. It supports clear decisions, repeatable workflows and accessible feedback."

# Mô tả ngắn 1–3 câu:

# - Đây là loại sản phẩm gì?

# - Ai sử dụng?

# - UI chủ yếu phục vụ công việc gì?

#

# Không mô tả chi tiết features/business rules ở đây.

#

# Ví dụ:

# "Design system for an internal business management application

# used by managers and employees for daily operational workflows."

colors:

primary: "#1A73E8"

# Màu thương hiệu / primary action chính.

# Ví dụ: "#2563EB"

primary-hover: "#1558B0"

# Trạng thái hover của primary.

surface: "#FFFFFF"

# Background chính của card, form, dialog.

surface-secondary: "#F8F9FA"

# Background phụ dùng để phân chia các vùng giao diện.

text-primary: "#202124"

# Màu chữ chính.

text-secondary: "#5F6368"

# Metadata, description, supporting information.

text-disabled: "#9AA0A6"

# Disabled state.

border: "#DADCE0"

# Border mặc định.

success: "#1E8E3E"

# Completed / approved / success states.

warning: "#F9AB00"

# Pending / attention required.

error: "#D93025"

# Error / rejected / destructive.

info: "#1A73E8"

# Informational states.

typography:

heading-xl:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "32px"
fontWeight: 700
lineHeight: "1.2"
# Dùng cho page title.

heading-lg:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "24px"
fontWeight: 600
lineHeight: "1.3"
# Major section / modal title.

heading-md:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "20px"
fontWeight: 600
lineHeight: "1.35"
# Card title / subsection.

body-md:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "16px"
fontWeight: 400
lineHeight: "1.5"
# Default UI text.

label-md:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "14px"
fontWeight: 500
lineHeight: "1.4"
# Buttons, labels, navigation.

caption:
fontFamily: "Roboto, Arial, sans-serif"
fontSize: "12px"
fontWeight: 400
lineHeight: "1.35"
# Metadata / timestamp / helper text.

spacing:

xs: 4px
sm: 8px
md: 16px
lg: 24px
xl: 32px
2xl: 48px

# Định nghĩa spacing scale dùng toàn project.

#

# Ví dụ:

# xs: 4px

# sm: 8px

# md: 16px

# lg: 24px

# xl: 32px

# 2xl: 48px

#

# Agent phải ưu tiên các giá trị này thay vì tự tạo spacing mới.

rounded:

sm: 4px
md: 8px
lg: 12px
full: 9999px

# Corner radius scale.

#

# Ví dụ:

# sm: 4px

# md: 8px

# lg: 12px

# full: 9999px

components:

button-primary:
backgroundColor: "{colors.primary}"
textColor: "{colors.surface}"
rounded: "{rounded.md}"
height: "{colors.surface}"
padding: "40px"

input:
backgroundColor: "{colors.surface}"
textColor: "{colors.text-primary}"
rounded: "{rounded.md}"
height: "0 16px"
padding: "40px"

card:
backgroundColor: "{colors.surface}"
rounded: "{rounded.lg}"
padding: "0 12px"

# Chỉ thêm component token nếu component đó thực sự cần

# visual token dùng chung.

#

# Ưu tiên reference token:

#

# "{colors.primary}"

#

# thay vì duplicate:

#

# "#2563EB"

---

# Overview

## Product Context

Mland is a bilingual workshop-booking and ring-commerce application. Customers use it occasionally to discover workshops, choose products and complete focused actions; Staff, Owner and Admin Technical use operational views repeatedly to manage records and decisions. Customer UI is action- and content-led, while operational UI is data- and task-led.

<!--
Mô tả ngắn loại giao diện mà design system này phục vụ.

Trả lời:

- Đây là loại application nào?
- Internal tool, SaaS, dashboard, ecommerce, consumer app...?
- Nhóm user chính là ai?
- Họ sử dụng UI thường xuyên hay thỉnh thoảng?
- UI thiên về data-heavy, content-heavy hay action-heavy?

Không đưa detailed feature requirements vào đây.

Feature/Spec là source of truth cho business behavior.
-->

## Design Character

The interface should feel:

* clear
* trustworthy
* modern
* warm but restrained

<!--
Điền các tính chất thị giác/UX.

Ví dụ:

- professional
- calm
- efficient
- dense
- minimal
- friendly
- premium
- playful
- technical
-->

## Design Priorities

When making design decisions, prioritize:

1. clarity of the next task
2. accessibility and readable feedback
3. consistent reusable patterns
4. efficient scanning for operations
5. appropriate visual warmth for customer journeys

<!--
Đây là thứ Agent dùng khi có nhiều cách thiết kế đều hợp lệ.

Ví dụ:

1. clarity
2. task efficiency
3. consistency
4. accessibility
5. aesthetics
-->

## Design Philosophy

Make the next customer decision obvious, then keep operational work calm, structured and easy to scan. Use the energetic clarity of a cinema booking journey for public flows and the restrained, information-led discipline of a modern developer console for management flows.

<!--
Mô tả nguyên tắc tổng quát.

Ví dụ:

"Optimize for repeated daily operations rather than visual novelty."

"Prefer progressive disclosure over displaying every available action."

"Prefer information density over decorative whitespace."

Phần này rất quan trọng vì nó giúp Agent giải quyết những trường hợp
DESIGN.md không có rule cụ thể.
-->

---

# Colors

## Color Usage

Use light neutral surfaces as the default canvas. Blue communicates the primary action and active state; semantic colors communicate real status only. Customer imagery may provide warmth and character, while controls and data remain visually quiet.

<!--
Giải thích cách sử dụng màu.

Không chỉ ghi màu nào là màu gì.

Ví dụ:

Primary color represents actionable emphasis rather than decoration.

Neutral surfaces should dominate the interface.

Semantic colors should only communicate semantic state.
-->

## Primary

Use primary blue for the single most important action in a region, active navigation, selected controls and keyboard focus. Do not use it as a decorative fill or to represent destructive actions.

<!--
Agent cần biết primary được dùng khi nào.

Ví dụ:

Use primary for:
- primary CTA
- active navigation
- selected interactive states

Do not use primary purely as decoration.
-->

## Surfaces

Use surface-secondary for the page canvas and low-emphasis grouped regions. Use surface for cards, forms, dialogs and tables. Separate adjacent surfaces with spacing and the border token before adding elevation.

<!--
Ví dụ:

Page background
→ secondary surface

Cards/forms/dialogs
→ primary surface
-->

## Text Colors

Use text-primary for headings, values and instructions. Use text-secondary for descriptions, metadata and supporting context. Use text-disabled only for unavailable controls and never for information that must be read.

<!--
Giải thích:

text-primary → main information
text-secondary → supporting information
text-disabled → unavailable state
-->

## Semantic Colors

success communicates a completed or approved outcome; warning communicates a pending state or required attention; error communicates failure, rejection or a destructive action; info communicates neutral guidance. Pair every semantic color with a readable text label.

<!--
Ví dụ:

success → completed / approved
warning → pending / attention
error → failed / rejected / destructive
info → neutral informational state
-->

## Color Constraints

* Do not invent new semantic colors.
* Do not communicate status using color alone.
* Do not use gradients unless Feature/Spec explicitly requires them.

<!--
Ví dụ:

- Do not invent new semantic colors.
- Do not communicate status using color alone.
- Do not use gradients unless explicitly required.
-->

---

# Typography

## Font Strategy

Use Roboto, Arial, sans-serif throughout the interface for a clear, familiar and technically neutral reading experience. Use monospace only for identifiers, logs, reference codes or technical values.

<!--
Ví dụ:

Use Inter throughout the application.

Use monospace only for identifiers, code, logs or technical values.
-->

## Type Hierarchy

Heading XL is the page title; Heading LG is a major section or modal title; Heading MD is a card or subsection title; Body MD is default UI text; Label MD is for controls and navigation; Caption is for metadata and helper text.

<!--
Mapping rõ:

Heading XL → Page title
Heading LG → Major section
Heading MD → Card/subsection
Body MD → Default UI text
Label MD → Control labels
Caption → Metadata
-->

## Typography Rules

* Avoid excessive font-size variation.
* Use weight before increasing font size when possible.
* Avoid more than three typography levels inside one content region.

<!--
Ví dụ:

- Avoid excessive font-size variation.
- Use weight before increasing font size when possible.
- Avoid more than three typography levels inside one content region.
-->

---

# Layout

## Application Shell

Customer pages use a simple sticky header, focused main content and a clear journey-oriented primary action. Operational pages use a desktop sidebar, top context area, toolbar and main content region; on smaller screens, the sidebar becomes a drawer.

<!--
Ví dụ:

Desktop:
Sidebar + Top Navigation + Main Content

Mobile:
Top Navigation + Drawer Navigation + Main Content
-->

## Content Width

Customer content uses a maximum width of 1200px, with focused forms limited to 640px. Operational tables may use the available desktop width; detail and edit views should remain comfortably readable rather than stretch content needlessly.

<!--
Ví dụ:

Maximum main content width: 1440px.

Data-heavy pages may use full available width.

Focused forms should use a narrower content column.
-->

## Page Padding

Desktop: 24–32px. Tablet: 20–24px. Mobile: 16px. Retain enough vertical spacing to separate tasks without creating empty decorative space.

<!--
Ví dụ:

Desktop: 24–32px
Tablet: 20–24px
Mobile: 16px
-->

## Grid

Use a 12-column desktop grid for dashboard summaries and content sections. Customer cards may use 3, 4, 6 or 12 columns; operational forms use one or two columns based on field relationships. Do not force focused forms into the dashboard grid.

<!--
Điền nếu project sử dụng grid.

Ví dụ:

12-column desktop grid.

Dashboard cards may span 3/4/6/12 columns.

Do not force forms into the dashboard grid.
-->

## Spacing Rules

xs is micro spacing; sm joins tightly related elements; md is component internal spacing; lg separates component groups; xl separates page sections; 2xl separates major journeys or page regions.

<!--
Ví dụ:

xs → micro spacing
sm → tightly related elements
md → component internal spacing
lg → component groups
xl → sections
2xl → major page separation
-->

## Responsive Strategy

Customer journeys are mobile-first: preserve the main decision and CTA, then stack supporting content. Operational interfaces are desktop-first but responsive: collapse navigation to a drawer, stack forms, wrap toolbars and prioritize table columns before allowing horizontal scroll.

<!--
Agent cần biết responsive philosophy.

Ví dụ:

Desktop-first.

At smaller breakpoints:
- collapse sidebar
- stack form columns
- move secondary actions into overflow
- allow tables to horizontally scroll only as last resort
-->

---

# Elevation & Depth

## Elevation Philosophy

Prefer light surfaces, borders and whitespace over permanent shadows. Elevation indicates an element layered above its current context, not a decorative style for every container.

<!--
Ví dụ:

Prefer flat surfaces and borders over shadows.

Use elevation only when one UI element physically overlays another.
-->

## Shadow Usage

Use shadows for:

* modals, drawers and popovers
* ordinary cards and decorative containers

Do not use shadows for:

* destructive or difficult-to-reverse actions
* a small focused edit or confirmation

<!--
Ví dụ:

Use:
- modal
- dropdown
- popover

Avoid:
- every card
- decorative containers
-->

## Visual Hierarchy

Preferred hierarchy mechanisms:

1. spacing
2. typography
3. surface and border
4. elevation only for overlays

<!--
Ví dụ:

1. spacing
2. typography
3. surface
4. border
5. elevation
-->

---

# Shapes

## Corner Strategy

Inputs and buttons use rounded.md; cards and dialogs use rounded.lg; badges and compact status chips may use rounded.full. Keep radius restrained and consistent.

<!--
Ví dụ:

Inputs/buttons → rounded.md
Cards/dialogs → rounded.lg
Badges → rounded.full
-->

## Shape Character

Use modest rounding, clean borders and compact controls. The interface should feel contemporary and approachable without becoming playful or overly soft.

<!--
Ví dụ:

Use restrained rounding to maintain a professional operational appearance.

Avoid excessive pill-shaped containers.
-->

## Shape Constraints

* Use the approved design tokens and the closest established pattern.
* Keep customer choices and operational actions easy to scan.
* Do not add decoration, component variants or layouts without a user task.

---

# Components

<!--
Đây là behavioral VISUAL convention.

Không mô tả business logic của feature.

Mỗi component nên trả lời:

- Khi nào dùng?
- Khi nào không dùng?
- Variant nào tồn tại?
- Hierarchy như nào?
- State nào bắt buộc?
-->

## Buttons

### Variants

* Primary
* Secondary
* Ghost
* Destructive

<!--
Ví dụ:

Primary
Secondary
Ghost
Destructive
-->

### Primary Button

Use for the one action that advances the current customer journey or completes the main operational task. Only one primary button should dominate a region.

### Secondary Button

Use for a valid alternative action that does not compete with the primary outcome, such as going back, previewing or opening supporting context.

### Destructive Button

Use only for a confirmed destructive action, such as cancel, reject or delete; it must use the error token rather than primary blue.

### Button Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

<!--
Ví dụ:

Only one primary action should dominate a region.

Do not use destructive styling for normal actions.

Icon-only buttons require accessible labels/tooltips.
-->

---

## Inputs

Every input presents a persistent label, the control, optional helper text and an adjacent validation message. Customer forms keep the next required decision visible; operational forms group related fields into clearly titled sections.

<!--
Ví dụ:

Input anatomy:

Label
Control
Optional helper text
Validation message
-->

### Input States

Define required states:

* default
* hover
* focus
* disabled
* error
* read-only

### Input Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

---

## Select / Combobox

Use a Select for a small, stable list that can be scanned. Use a Combobox when the dataset is large, searchable or frequently filtered; show the current selection and provide an empty-result state.

<!--
Ví dụ:

Select:
small predefined datasets.

Combobox:
large/searchable datasets.
-->

---

## Checkbox / Radio / Switch

Use Checkbox for multiple independent selections, Radio for exactly one choice from a short visible set, and Switch only for an immediately applied boolean setting. Do not disguise these controls as one another.

<!--
Phân biệt rõ:

Checkbox → multiple independent selections
Radio → exactly one option
Switch → immediate boolean state
-->

---

## Card

Use cards for a self-contained item, a customer choice, a summary or a distinct dashboard metric. Use ordinary sections for long forms, collections and sequential information; do not put every content block inside a card.

<!--
Quan trọng:

Không biến mọi thứ thành card.

Mô tả khi nào content cần card và khi nào chỉ cần section.
-->

---

## Data Table

### Usage

Use a data table for operational collections where users compare multiple records, scan statuses or take row-level actions. Use a list or card grid for customer-facing choices where imagery and short descriptions matter more than comparison.

### Anatomy

A standard table contains a toolbar with search, visible frequent filters and secondary actions; a table with clear headers and rows; and pagination when the server returns more than one manageable page.

<!--
Ví dụ:

Toolbar
├── Search
├── Filters
└── Actions

Table
├── Header
├── Rows
└── Row Actions

Pagination
-->

### Table Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

### Row Actions

Show one or two frequent, low-risk row actions directly when there is room. Put rare, secondary or destructive actions in an overflow menu, with a confirmation where the action is difficult to reverse.

<!--
Ví dụ:

1–2 frequent actions → direct actions.

Many/rare actions → overflow menu.
-->

---

## Modal

### Use Modal For

* large multi-step workflow or large data-entry form
* large multi-step workflow or large data-entry form

### Do Not Use Modal For

* large multi-step workflow or large data-entry form
* large multi-step workflow or large data-entry form

<!--
Ví dụ:

Use:
confirmation
small focused edits

Avoid:
large multi-step workflow
large data-entry forms
-->

---

## Drawer

Use a drawer for mobile navigation, contextual filters or inspecting a compact entity without losing the collection context. Do not use a drawer for a long multi-step edit flow.

<!--
Ví dụ:

Use drawer when users need to inspect/edit an entity
without losing context from the collection page.
-->

---

## Tabs

Use tabs for peer sections of one entity or one page context, such as details and activity. Do not use tabs as a substitute for primary navigation or to hide the next required customer decision.

<!--
Ví dụ:

Use tabs for peer sections belonging to the same entity/context.

Do not use tabs as a substitute for primary navigation.
-->

---

## Badge / Status

Use a compact badge or status label with semantic color, readable text and optional supporting icon. Status labels must remain understandable in monochrome and in screen-reader output.

<!--
Agent cần biết:

- semantic color mapping
- text requirements
- whether icon is allowed
- size/density
-->

---

## Navigation

Customer primary navigation stays short and task-oriented in the header. Operational primary navigation uses a sidebar; context navigation uses tabs; hierarchy navigation uses breadcrumbs. Do not introduce another navigation system without a documented need.

<!--
Ví dụ:

Primary navigation → sidebar.

Context navigation → tabs.

Hierarchy navigation → breadcrumbs.

Do not invent additional navigation systems.
-->

---

## Search

Make search visible in operational collections where lookup is common. Debounce server-backed searches, retain the entered term and explain when no records match.

<!--
Ví dụ:

Search should be visible when searching is a frequent collection action.

Use debounced search for server-backed collections where appropriate.
-->

---

## Filters

Keep frequent filters visible in the toolbar; place advanced or rare filters in a panel or drawer. Show active filters as removable values and provide a clear reset action.

<!--
Ví dụ:

Frequently used filters remain visible.

Advanced/rare filters may use a filter panel/popover.

Active filters must remain visible and removable.
-->

---

## Pagination

Use pagination for large server-backed collections. Show the current page and total information when available, preserve filters and search when pages change, and avoid pagination for a small static choice set.

<!--
Ví dụ:

Use pagination for large server-backed collections.

Show current page and total information when available.
-->

---

## Toast / Notification

Use a toast for brief success or non-blocking operation feedback. Use an inline alert for information that needs continued attention. Use a modal only when a user decision is required before continuing.

<!--
Phân biệt:

Toast → temporary operation feedback.

Inline alert → information requiring continued attention.

Modal → requires user decision.
-->

---

# UI States

<!--
Agent không được chỉ implement "happy path".
-->

## Loading

Use skeletons for content regions and a small spinner in a pending button. Preserve the page structure and already loaded data whenever possible; do not replace the whole page with a spinner for a small update.

<!--
Ví dụ:

Content region → skeleton.

Small button operation → spinner.

Never replace the entire page with a spinner when avoidable.
-->

## Empty

An empty state explains what is absent, why it may be absent and the next useful action. It uses a concise message and one relevant CTA when the user can resolve the situation.

<!--
Empty state nên trả lời:

1. What is missing?
2. Why is it empty?
3. What can the user do?
-->

## Error

An error states what failed in plain language, retains recoverable input and provides a practical recovery action such as retrying, correcting a field or contacting support when appropriate.

<!--
Error nên nói:

what failed
+
how to recover
-->

## Disabled

Disabled controls use text-disabled and reduced emphasis but maintain readable contrast. Explain the reason and prerequisite nearby when a user could reasonably expect the control to be available.

## Permission Denied

Show a concise permission-denied view or inline message that identifies the unavailable area and offers a safe next step. Do not reveal protected data, hidden actions or authorization logic.

<!--
Nếu application có roles/permissions,
định nghĩa visual behavior nhưng KHÔNG định nghĩa authorization logic.
-->

## Partial / Missing Data

Show the available information, identify what could not be loaded and offer retry where it is safe. Do not present partial operational data as complete.

---

# Interaction Patterns

## Confirmation

Require confirmation for destructive, irreversible or financially meaningful actions. Name the affected entity and consequence in the confirmation; avoid generic wording such as “Are you sure?”.

<!--
Ví dụ:

Require confirmation for destructive or difficult-to-reverse actions.

Confirmation must identify the affected entity.

Prefer:
"Cancel booking for Nguyễn Văn A?"

Avoid:
"Are you sure?"
-->

## Destructive Actions

Use destructive styling only for delete, cancel, reject or similarly harmful actions. Keep it visually secondary until confirmation and preserve enough context for the user to understand the impact.

## Form Submission

Disable duplicate submission while a request is pending, keep entered values on recoverable client or server errors, and report success near the completed action without hiding important next steps.

<!--
Ví dụ:

Disable duplicate submission while request is pending.

Keep user input when recoverable validation/server errors occur.
-->

## Validation

Show field validation next to the relevant field after interaction or submission. Put cross-field validation in the relevant form section. Preserve the meaning of server and business validation messages.

<!--
Ví dụ:

Field validation → next to field.

Cross-field validation → relevant form section.

Server/business validation → preserve server/domain message meaning.
-->

## Progressive Disclosure

Keep the next customer decision, price/context needed for that decision and frequent operational actions visible. Put rare, advanced or secondary controls into progressive disclosure without hiding information required to complete the task.

<!--
Agent dùng rule này để quyết định:
cái gì visible ngay,
cái gì overflow,
cái gì advanced.
-->

---

# Page Patterns

<!--
Định nghĩa reusable page archetypes.

Feature sẽ map vào pattern.

KHÔNG tạo section riêng cho từng feature.
-->

## Collection Page

Use for:

bookings, workshop sessions, ready-ring catalogue items, custom orders, invoices and operational records.

### Structure

Page Header
├── Title
├── Description
└── Primary Action

Toolbar
├── Search
├── Filters
└── Secondary Actions

Content
└── Table / List / Grid

Footer
└── Pagination

### Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

---

## Detail Page

Use for:

booking details, workshop session details, member profiles, ring catalogue items, orders and invoices.

### Structure

Page Header
├── Back Navigation / Breadcrumb
├── Entity Identity
├── Status
└── Actions

Primary Information

Related Sections

Activity / History

### Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

---

## Create / Edit Page

### Structure

Page Header

Form
├── Section
├── Section
└── Section

Actions
├── Cancel
└── Save

### Rules

* Group related fields under concise section headings.
* Keep Cancel and Save visible at the end of the form.
* Do not split an ordinary form into a wizard without a Feature/Spec requirement.

---

## Dashboard

### Purpose

The dashboard provides a concise operational overview and directs Staff or Owner to the next action; it is not a dumping ground for every metric.

### Information Priority

1. Bookings and workshop sessions needing an operational decision
2. Payment, exception and capacity status requiring follow-up
3. Recent activity and trends that provide context for the next action

### Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

<!--
Dashboard không mặc định là nơi nhét mọi metric.
Chỉ đưa thông tin phục vụ overview/decision/action.
-->

---

## Settings Page

Group settings by a clear ownership or purpose, use descriptive section headings and show save/apply feedback close to the changed setting. Keep high-risk account and integration settings visually distinct.

---

## Wizard / Multi-Step Flow

Use a wizard only when Feature/Spec defines a genuinely sequential, multi-step process whose earlier answers affect later steps. Do not introduce wizard flows for ordinary forms.

<!--
Nếu application không cần wizard:
ghi rõ "Do not introduce wizard flows unless required by Feature/Spec."
-->

---

# Responsive Patterns

## Desktop

Customer pages use a readable centered content area with supporting imagery where it improves choice. Operational pages show sidebar navigation, full table toolbars and two-column forms when field relationships benefit from comparison.

## Tablet

Reduce columns and imagery before reducing readability. Customer cards reflow to two columns when appropriate; operational sidebars collapse when content would become cramped.

## Mobile

Show a compact header, one-column content and a persistent or clearly repeated primary action for customer journeys. Operational toolbars wrap or move secondary controls to overflow; tables prioritize key columns or use a detail alternative.

## Responsive Transformation Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

<!--
Ví dụ:

Sidebar → drawer
Multi-column form → single column
Toolbar → wrapped/overflow
Table → prioritized columns / responsive alternative
-->

---

# Accessibility

## General

* Use semantic HTML and accessible names for all controls.
* Meet WCAG 2.1 AA contrast and provide visible focus.
* Do not use color, position or hover alone to convey essential information.

## Keyboard

All interactive elements are reachable and usable by keyboard in a logical order. Support Enter for the primary form action when appropriate, Escape for dismissible overlays, and visible keyboard operation for menus, tabs and drawers.

## Focus

Use a visible 2px primary-blue focus outline with sufficient offset. Move focus into opened modal or drawer content, return it to the triggering control when closed, and never remove focus indication.

## Contrast

Meet WCAG 2.1 AA contrast for text, controls, focus indication and semantic states. Verify contrast against each surface token; do not rely on opacity alone for disabled or secondary information.

## Icons

Use a consistent outline icon set. Icons support labels instead of replacing ambiguous actions; icon-only controls require an accessible name and tooltip.

## Status

Never communicate critical state using color alone.

Status changes that affect the current task use appropriate live-region announcements where needed, while routine visual updates should not interrupt screen-reader users.

---

# Content & UI Copy

## Tone

Concise, professional, welcoming and action-oriented. Customer copy is reassuring and easy to scan; operational copy is precise and uses the established domain terminology.

<!--
Ví dụ:

Concise
Professional
Action-oriented
Domain-specific
-->

## Labels

Use specific verbs and nouns that name the outcome, such as “Đặt workshop”, “Lưu thay đổi” or “Xác nhận thanh toán”. Avoid generic labels such as “Submit” when the operation can be named.

<!--
Ví dụ:

Prefer:
"Create booking"

Avoid:
"Submit"
when the actual operation is creating a booking.
-->

## Terminology

Feature and domain terminology from project specifications is authoritative.

Do not rename domain concepts merely for visual consistency.

Use Vietnamese as the default customer language with an explicit English alternative where supported. Preserve terms defined by the project specifications and do not rename roles, booking states or payment concepts for visual preference.

## Error Messages

State the problem, the affected action and the next recovery step without exposing technical details, tokens, account information or internal implementation language.

---

# Icons

## Icon System

Use a single consistent outline icon library or equivalent existing SVG set. Icons use the current text or semantic color token and are sized to support, not dominate, their label.

<!--
Ví dụ:

Use Lucide icons.

Do not mix icon libraries unless required by existing codebase.
-->

## Icon Rules

* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.
* Do not add decoration, component variants or layouts without a user task.

<!--
Ví dụ:

Icons supplement labels; they do not replace ambiguous actions.

Keep stroke style consistent.
-->

---

# Motion

## Motion Philosophy

Motion communicates navigation, loading and state change. Keep duration short and subtle, honor prefers-reduced-motion and never make motion a requirement for understanding an action.

<!--
Nếu business application:

Motion should communicate state changes,
not serve as decoration.
-->

## Allowed Motion

* short feedback for completed or changed UI state
* subtle enter/exit transition for overlays and navigation context

## Avoid

* long decorative animation
* motion that delays input, obscures content or ignores reduced-motion preference

---

# Existing UI Integration

<!--
Rất quan trọng đối với EXISTING PROJECT.
-->

## Reuse Policy

Before creating a new component, inspect the prototype and any reusable component introduced later. Extend the closest existing component when the visual and semantic concept match; do not refactor unrelated prototype UI during feature work.

<!--
Ví dụ:

Before creating a new component, inspect the existing component library.

Prefer extending an existing component when the required behavior
belongs to the same visual/semantic concept.
-->

## Existing Components

The current prototype is a static customer UI in frontend/index.html with interaction rendering in frontend/app.js. It has no reusable component library yet; reusable components should be introduced only when a feature establishes a repeated visual and semantic pattern.

<!--
Không cần list toàn bộ component nếu chúng thay đổi thường xuyên.

Ví dụ:

Reusable UI components live under:
src/components/ui/

Feature-level components live under:
src/features/
-->

## Existing Styles

Existing global tokens and component styles are in frontend/styles.css. New UI should adopt this DESIGN.md’s approved tokens; migration of the current gold prototype is a separate, explicitly scoped task.

<!--
Ví dụ:

Global tokens:
src/styles/tokens.css

Component styles:
CSS modules colocated with components.
-->

## Legacy UI

Do not copy legacy visual inconsistencies into new work and do not refactor unrelated screens. When an existing style conflicts with DESIGN.md, preserve compatibility for the existing screen and apply DESIGN.md to new UI unless Feature/Spec directs a scoped migration.

<!--
Ví dụ:

Do not copy legacy visual inconsistencies into new features.

Do not refactor unrelated legacy UI while implementing a feature.

When DESIGN.md conflicts with legacy styling,
use DESIGN.md for newly created UI unless Feature/Spec says otherwise.
-->

---

# Agent Decision Rules

<!--
Đây là section cực kỳ quan trọng nếu DESIGN.md được dùng bởi coding Agent.
-->

## Source of Truth

When implementing UI, use the following priority:

1. Business/domain requirements
2. Feature specification
3. Existing application behavior that must remain compatible
4. DESIGN.md
5. Existing reusable UI components
6. Agent preference

<!--
Điều chỉnh thứ tự nếu architecture project của bạn yêu cầu khác.

Quan trọng nhất:
Agent không được thay đổi business behavior chỉ vì UI khác đẹp hơn.
-->

## Before Designing

Before creating or modifying UI:

1. Read the relevant Feature.
2. Read the relevant Spec.
3. Read DESIGN.md.
4. Inspect the existing page being modified.
5. Inspect reusable components.
6. Identify the appropriate page pattern.
7. Identify required UI states.
8. Implement using existing patterns whenever possible.

## Feature → UI Mapping

Feature/Spec defines:

* required capability
* business behavior
* domain rules
* permissions
* required data
* validation requirements

DESIGN.md defines:

* visual hierarchy
* component selection conventions
* layout
* interaction presentation
* responsive behavior
* visual states

The Agent must not infer new business behavior from DESIGN.md.

## Missing Design Rule

When DESIGN.md does not define a visual decision:

1. Look for an established pattern in the existing application.
2. Reuse the closest existing pattern when appropriate.
3. Apply the general principles from DESIGN.md.
4. Introduce the smallest new design concept necessary.
5. Do not create a new design system or visual language.

## Missing Business Rule

When Feature/Spec does not define required business behavior:

Do not invent the rule from visual conventions.

Stop and report the ambiguity before inventing business behavior. Implement only the behavior explicitly supported by the Feature/Spec or existing compatible domain/API behavior.

<!--
Ví dụ:

Stop and report the ambiguity.

OR:

Implement only the behavior explicitly supported by existing domain/API behavior.
-->

---

# Do's

* Reuse existing components and approved tokens.
* Follow the applicable page pattern and include required UI states.
* Preserve domain terminology and keep repeated workflows consistent.

<!--
Recommended baseline:

- Reuse existing components.
- Use design tokens.
- Follow established page patterns.
- Preserve domain terminology.
- Design loading, empty, error and disabled states.
- Maintain accessible contrast.
- Keep repeated workflows visually consistent.
- Optimize important workflows for scanning and efficiency.
-->

---

# Don'ts

* Do not invent arbitrary colors, spacing or component variants.
* Do not change business behavior for visual convenience.
* Do not duplicate an existing pattern with only minor styling differences.

<!--
Recommended baseline:

- Don't invent colors.
- Don't invent arbitrary spacing.
- Don't invent component variants without need.
- Don't create unique layouts for every feature.
- Don't place everything inside cards.
- Don't use excessive shadows.
- Don't use decorative gradients unless specified.
- Don't hide important actions unnecessarily.
- Don't change business behavior for visual convenience.
- Don't duplicate an existing component with slightly different styling.
-->

---

# Project-Specific Design Constraints

Customer booking and commerce pages must keep the next required decision and one primary CTA easy to find on mobile. Staff and Owner operational collections prefer tables to decorative card grids. New UI must remain usable at 1366×768 and on mobile touch screens. Public presentation may take inspiration from the prioritization of CGV Vietnam; colors, typography, interaction and information presentation take inspiration from Firebase. Do not use either organization’s logo, name, visual assets or distinctive layout as Mland UI.

<!--
Chỉ đặt DESIGN constraints đặc thù của project ở đây.

Ví dụ:

- Application must remain usable on 1366×768 displays.
- Tables are preferred over cards for operational collections.
- Managers primarily use desktop.
- Touch targets must still support tablet use.

KHÔNG đặt business rules như:

"Employees can only cancel shifts 24 hours in advance."

Rule đó phải nằm trong Feature/Spec.
-->

---

# References

References: the current customer prototype in frontend/, the approved feature/spec documents, and public CGV Vietnam and Firebase interfaces as high-level UX references only. No external brand asset, logo, proprietary typeface or distinctive screen composition is to be copied.

<!--
Optional.

Có thể ghi:

- Figma page/frame
- existing screen considered canonical
- internal design system docs
- component library
- visual references

Không copy Feature/Spec vào đây.
-->
