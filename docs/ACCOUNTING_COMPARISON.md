# All Banks vs Xero, QuickBooks and FreeAgent

How the three mainstream accounting products turn a raw bank feed into bookkeeping, how All Banks compares today, and what to change so it works for you day to day **and** for an accountant at quarter-end and year-end.

Confidence markers: **[S]** = backed by the sources at the bottom. **[K]** = general product knowledge, not confirmed in this research. Check those against each vendor's current help pages before relying on them. This is a product comparison, not tax advice.

---

## 1. The pipeline the accounting apps share

All three follow the same seven steps. The differences are in how much each step is automated.

1. **Import**: bank feed lines arrive (open banking or direct feed), de-duplicated against what is already there.
2. **Auto-explain**: rules and "guesses" propose a category, payee, VAT rate or a match to an existing invoice, bill or transfer.
3. **Review queue**: nothing is final until a human accepts it. QuickBooks calls it **For Review** [S]; FreeAgent flags guessed items in orange for approval [S]; Xero shows suggested matches to accept [S].
4. **Match before you create**: link a feed line to something that already exists (invoice, bill, transfer, manual entry) rather than creating a duplicate. QuickBooks warns that adding instead of matching double-counts income or expense [S].
5. **Reconcile**: confirm the books agree with the bank statement balance. [K]
6. **Lock the period**: stop changes to closed periods. Xero's lock dates are changeable only by advisor-level users and are recorded in History & Notes [S].
7. **Report and hand over**: P&L, balance sheet, VAT return, with accountant access to the same file. [K]

### Categorisation and rules, side by side

| Mechanism | Xero | QuickBooks Online | FreeAgent | All Banks today |
|---|---|---|---|---|
| Rules | Bank rules (Accounting > Bank Accounts > Manage Account > Bank Rules) auto-categorise recurring items [S] | Rules match on description, bank text or amount, assign payee, category or split; run in priority order; copy/disable/delete [S] | "Guess" learns from similar past explanations. A separate user-defined rules engine was not found in the sources [S] | Rules on description/counterparty/merchant, contains/equals/regex, direction, amount range, from/to bank, priority, live match preview |
| Suggested matches | Suggests invoices/bills to match [S] | Matches vs adds [S] | Guess order: manual entries, PayPal, transfers between your accounts, invoice payments, bill payments, then similar past items [S] | Transfer pairing only (amount, ±3 days, text hints) |
| Manual-match tolerance | Partial payments not auto-matched [S] | n/a | Same amount, date ±1 day [S] | Same amount, ±3 days, 120-day window |
| Transfers between own accounts | Transfer type [K] | Filter for transfers between two accounts [S] | "Transfer to/from another account" type [S] | Auto-link plus confirm queue, unlink and remember rejection |
| Bulk actions | Bulk coding except on the cheapest plan [S] | Batch actions [K] | Bulk explain similar descriptions, one type/VAT/category; not for invoice/bill types [S] | None (one at a time) |
| Split a transaction | Yes [K] | Yes, via rules or manually [S] | Yes [K] | **No** |
| Payee/merchant renaming | Yes [K] | Renaming rules [S] | Contact on explanation [K] | Merchant from Endute enrichment only |
| Rule creation from a decision | Offered when coding [K] | "Create a rule" link on review items [S] | Learns automatically via Guess [S] | "Remember for next time" toggle |
| Category set | Chart of accounts [K] | Chart of accounts [K] | Accounting categories split into VAT-able and zero-rated, custom allowed [S] | 14 spending categories plus optional subcategories |
| Tax treatment per line | Tax rates on coding [K] | Sales-tax/VAT codes [K] | VAT drop-down: Auto, specific rate, custom, out of scope [S] | **None** |

### Accountant-facing capability

| Capability | Xero | QuickBooks | FreeAgent | All Banks today |
|---|---|---|---|---|
| Chart of accounts / double-entry | Yes [K] | Yes [K] | Yes [K] | **No** (single-entry list) |
| Business vs personal separation | Separate orgs or accounts [K] | Same [K] | Types such as drawings/capital [K] | **No flag** |
| Receipts / attachments | Files, per transaction on some screens [S] | Yes [K] | Yes [K] | **No** (notes only) |
| Reconcile to statement balance | Yes [S][K] | Yes [K] | Yes [K] | **No** (balances shown, not reconciled) |
| Period / lock date | Lock dates, advisor-only change, logged [S] | Closing date [K] | Accounting-period locking [K] | **No** |
| Audit trail | History & Notes; Xero says a full every-event trail is not planned [S] | Audit log [K] | Audit via changes [K] | **No** (edits overwrite) |
| VAT return | Yes [K] | Yes [K] | Yes, incl. HMRC payments handled as types [S] | **No** |
| Tax-year / quarter views | Reports by period [K] | Reports by period [K] | Tax-year aware [K] | Month filter only |
| Accountant access | Invite advisor [K] | Invite accountant [K] | Invite accountant [K] | Export only |
| Reports | P&L, balance sheet, trial balance [K] | Same [K] | Same plus tax timeline [K] | Month in/out, category donut |
| Multi-currency | Yes [K] | Yes [K] | Yes, limited [K] | Totals per currency, no conversion |

---

## 2. Where All Banks stands

**It's already strong in:** privacy and security (encrypted DB, Keystore key, biometric lock, no cloud), transfer detection with a confirm queue, a reasonable rules engine with a live match preview and from/to bank, concise categories with subsections, check-later triage, recurring-payment detection, budgets, backups, and a calm UI.

**It's a personal-finance viewer, not a bookkeeping tool.** The core gap is that every transaction is a single line with one category. Accountants need each line to carry: *is it business or personal, what P&L or tax box does it belong to, what VAT, is there a receipt, has it been reconciled, and who changed it.*

### Flaws, in order of impact for bookkeeping and year-end

1. **No business/personal flag.** Mixed accounts can't be separated. Personal spending pollutes the expense totals. Accountants need drawings, owner's introduction of capital and private use clearly marked.
2. **Categories aren't tax-aware.** "Dining & takeaways" is useful to you. For a sole trader it might be disallowable entertainment or allowable subsistence. Categories need a **type** (income, expense, transfer, non-P&L) and an optional **tax-return mapping**. Third-party guides map UK expenses onto SA103F headings such as cost of goods, premises, travel and motoring, admin, finance charges and professional fees; capital purchases and loan repayments are treated differently [S].
3. **No VAT.** No net/gross/VAT split, no rate, no "out of scope".
4. **No split transactions.** One supermarket receipt covering business and personal, or one payment covering two categories, can't be recorded correctly.
5. **No receipts.** HMRC expects evidence behind expenses; an accountant will ask.
6. **No reconciliation.** There's no "statement balance X, books say Y, difference Z" check. Balances are fetched but never proven. The balance trend chart also assumes the imported history is complete.
7. **No audit trail.** Re-categorising, linking/unlinking transfers and rule re-runs overwrite silently. Accountants want who/when/before/after.
8. **No period lock or "year-end close".** You can edit last year's transactions after handing them over.
9. **Month filter, not tax year or quarter.** UK tax year is 6 April to 5 April; MTD quarters follow.
10. **Export is minimal.** A CSV with no tax code, VAT, receipt reference, reconciled flag, running balance, or summary sheet.
11. **Rules can't do enough.** They can't rename the payee, split, set VAT or business/personal, and they don't show hit counts, last-used, or the auto-suggested rule. The same rule feeds past and future.
12. **Transfers are bank-to-bank only.** Credit-card repayments, loan payments (capital vs interest), director's-loan style movements and refunds need dedicated handling.
13. **Dependence on Endute's history and fields.** No pending flag, no counterpart account details, and an unknown history depth; this limits opening balances and matching.
14. **Review isn't a workflow yet.** No bulk categorise, no undo, no "what's left before this month is done" checklist.

### Usability flaws

- Categorising is still one transaction at a time; there is no multi-select.
- No undo after Save, Link, Unlink or Merge.
- No first-run guidance (add key, pick business/personal for each account, set categories).
- Rule creation isn't proactive: the app could notice you categorised the same merchant three times and offer a rule.
- Currency totals with several currencies show separately with no conversion or warning.
- No accessibility pass (TalkBack labels, large-text testing).

---

## 3. Recommended improvements

### Phase 1: bookkeeping foundations (highest value)
1. **Account purpose** (Business / Personal / Mixed) and a per-transaction **Business / Personal / Drawings** flag with default from the account; rules can set it.
2. **Category types and tax mapping**: type (Income, Expense, Transfer, Non-P&L), optional tax-return heading, "allowable?" hint. Ship a UK sole-trader preset (SA103F headings [S]); allow editing.
3. **Split transactions** (amounts across categories, flags and VAT).
4. **Receipt attachments** from camera or gallery, stored encrypted, referenced by file name in exports.
5. **Reconciliation**: enter the statement closing balance and date, show difference, mark transactions reconciled, and keep opening balance and history-start date per account.
6. **Audit log**: append-only record of category, flag, split, link/unlink, rule-run and lock changes.
7. **Tax-year, quarter and custom date-range picker** across Activity, Overview and exports.

### Phase 2: accountant handover
8. **Accountant pack export** (XLSX, optionally CSV): Transactions (date, account, bank, payee, description, in/out, category, tax heading, VAT, business/personal flag, receipt file, note, transfer id, reconciled, running balance), Summary by category and by tax heading, Transfers list, Uncategorised list, Reconciliation sheet, Opening/closing balances. Add a zip of receipts.
9. **Period lock** with a clear "Locked to 5 Apr 2026" banner. Edits to locked periods need an explicit unlock that is recorded in the audit log (modelled on Xero's lock dates [S]).
10. **Month-end and year-end checklist** screen: all categorised, all transfers resolved, every account reconciled, receipts attached where required, nothing in check-later. Completing it sets "Ready for accountant".
11. **VAT**: per-line rate with net/VAT/gross, auto from category as FreeAgent does [S], plus a VAT summary export. Only needed if you're registered; make it optional.

### Phase 3: smarter, faster categorising
12. **Bulk actions**: multi-select to categorise, flag, or create one rule (FreeAgent's bulk explain shows the pattern [S]).
13. **Rules v2**: set payee name, business/personal, VAT, split percentages and note; show hit count and last-used; stop-processing option; "apply to history" vs "future only"; auto-suggest after repeated manual corrections.
14. **Guess order like FreeAgent's**: manual entries, transfers, recurring payments you've already categorised, then similar past items, then rules [S].
15. **Specialist transfer types**: credit-card repayment, loan repayment (capital vs interest split), refund/chargeback linking to the original, owner introduction/drawings.
16. **Undo** for the last action, and a visible "recently changed" list.

### Phase 4: tax-year support
17. **Quarterly summary** per category group for Making Tax Digital for Income Tax. Sources say quarterly updates summarise income and expenses for the three-month period, with year-end adjustments done separately; the first update for 2026/27 was due 7 August 2026, with a £50,000 qualifying-income wave and £30,000 planned from April 2027 [S]. **All Banks can't submit to HMRC**; that needs HMRC-recognised software via their API. The realistic goal is producing the figures your accountant or software needs, or exporting in a format a bridging tool accepts.
18. **Mileage and home-office** logs (flat-rate mileage: 45p per mile for the first 10,000 and 25p after [S]); simple, high-value for sole traders.
19. **Capital items** tagging (equipment purchases are handled through capital allowances, not ordinary expenses [S]).

### What not to build
Invoicing, payroll, inventory and a full ledger/balance sheet. They'd turn this into a worse Xero. Focus on categorised, reconciled, evidenced bank data that an accountant can trust and import.

### Security note for accountant access
Keep the on-device, no-cloud model. Share by **encrypted export** (the backup already uses AES-GCM with a passphrase) or a time-limited share, rather than adding accounts or a server.

---

## 4. Suggested first build

If you want one slice that makes the biggest difference to your accountant: **account purpose + business/personal flag, category types with the SA103F preset, tax-year picker, receipts, reconciliation, and the accountant-pack export.** That's items 1, 2, 4, 5, 7 and 8.

## Sources

- Xero: [Reconcile bank transactions quickly](https://www.xero.com/accounting-software/bank-reconciliation/), [UK step-by-step guide](https://marcandrews.com/?p=3583), [Keep tutorial](https://trykeep.com/newsroom/how-to-reconcile-in-xero-a-detailed-tutorial), [Xero Ideas: lock dates in History & Notes](https://productideas.xero.com/forums/939198-for-small-businesses/suggestions/45631876-history-record-changes-to-lock-dates)
- QuickBooks: [Categorize and match bank transactions](https://dancingnumbers.com/?p=89387), [Intuit renaming rules](https://quickbooks.intuit.com/learn-support/en-us/help-article/bank-feeds/use-renaming-rules-bank-feeds/L7m7kwjDP_US_en_US), [Intuit banking chapter](https://www.intuit.com/oidam/intuit/ic/en_ca/content/Intuit-education-program-ca-qbo-ch6-banking-in-quickbooks-online.pdf), [Bank feeds and rules study guide](https://open-exam-prep.com/study-guides/cpb-bookkeeper/qbo-setup-banking-lists/qbo-bank-feeds-rules), [About SA103F categories](https://quickbooks.intuit.com/learn-support/en-uk/self-employment-taxes/about-sa103f-categories/00/239287)
- FreeAgent: [How does Guess explain your transactions?](https://support.freeagent.com/hc/en-gb/articles/115001586004), [Choose the type and category](https://support.freeagent.com/hc/en-gb/articles/5545357536914), [Explain in bulk](https://support.freeagent.com/hc/en-gb/articles/4407182814354), [Explain a VAT payment to HMRC](https://support.freeagent.com/hc/en-gb/articles/360021705739)
- MTD for Income Tax: [GOV.UK: 436,000 sole traders and landlords make their tax digital](https://www.gov.uk/government/news/436000-sole-traders-and-landlords-make-their-tax-digital), [TaxAssist: how to make a quarterly update](https://www.taxassist.co.uk/resources/articles/making-tax-digital-for-income-tax-how-to-make-a-quarterly-update), [NI Business Info](https://www.nibusinessinfo.co.uk/content/deadline-approaching-first-making-tax-digital-quarterly-update)
- UK expense categories and mileage: [GoSimpleTax expenses list](https://support.gosimpletax.com/portal/en/kb/articles/self-employed-simpletax-s-expenses-list), [Forma self-employed expenses](https://www.goforma.com/tax/self-employed-expenses-tax-allowances)

**Limits of this research:** several sources are third-party guides or community forums. The official HMRC SA103F box list and each vendor's current rules documentation weren't retrieved, so mappings should be confirmed with your accountant before you build on them.
