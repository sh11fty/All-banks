# Categorising, rules and look: All Banks vs the best apps

Personal use only: no business, VAT or tax-return features. The question is how easy it is to categorise, how rules work, and how the app looks and feels, compared with Xero, QuickBooks, FreeAgent and the leading personal-finance apps (Monarch, Copilot, YNAB).

Markers: **[S]** = from the sources at the bottom. **[K]** = my general knowledge, not confirmed in this research.

---

## 1. What the best apps do

### Accounting apps (rules and review)
- **Review queue, nothing final until accepted.** QuickBooks puts new items in a *For Review* tab and applies rules there [S]. FreeAgent flags guessed items for approval [S]. Xero suggests matches you accept [S].
- **Rules in priority order** on description, bank text or amount, then set payee, category or split. QuickBooks also shows a "Create a rule" link right on the item being reviewed [S].
- **Guess from history.** FreeAgent tries manual entries, transfers, invoice/bill payments, then *similar past items*, and stops at the first hit [S].
- **Bulk explain.** FreeAgent filters for similar descriptions and applies one category to all [S]. Xero has bulk coding [S].
- **Match before create** to avoid double counting [S].

### Personal-finance apps (the closest to what you want)
- **Copilot:** rules aren't just name-based; it shows **all related transactions and asks you to confirm**, with a **preview of affected transactions before a rule is created** [S].
- **Monarch:** rules on merchant (exact or contains), amount, category or account, with actions to **rename the merchant, set the category, add tags, hide from budget and cash flow, set review status**; a **preview tab** before saving; a **review queue** you can drive from rules; **merchant merging** in settings; **bulk edit** [S].
- **YNAB:** pick a category for a payee once and later transactions from that payee are categorised automatically; bulk categorise; switch auto-categorise off per payee [S].

### Common pattern
1. Learn from the first correction, then confirm what else it will change.
2. Clean merchant names so rules and reports stay tidy.
3. Show *why* something was categorised and let you fix the cause (the rule), not just the symptom.
4. Treat "review" as a short, finishable queue, not a list.

---

## 2. Side-by-side with All Banks

| Area | Best apps | All Banks today | Gap |
|---|---|---|---|
| First categorisation | Predict from payee/history (YNAB, FreeAgent Guess) [S] | Endute's label mapped to 14 categories, else Other | No history-based suggestion |
| Fix one transaction | One tap, rule offered inline (QuickBooks) [S] | Bottom sheet with details, picker, rule toggle, note | Heavy for a quick fix; many steps |
| Bulk | Bulk edit / bulk explain [S] | None | **Missing** |
| "Apply to similar" | Shows related transactions, asks to confirm (Copilot) [S] | Silently re-runs rules on history | **No preview or feedback** |
| Rule actions | Category, rename merchant, tags, hide, review status (Monarch) [S] | Category only | Narrow |
| Rule preview | Preview tab (Monarch, Copilot) [S] | Live "matches N" count | Count only, no list |
| Rule builder wording | Plain if/then | "field / matchType / regex" | Technical |
| Merchant cleanup | Merge merchants (Monarch) [S] | None | **Missing** |
| Explain a result | Rule/source visible [K] | Not shown | **"Why is this Groceries?" unanswered** |
| Review queue | Finishable, status per item, rules can set it (Monarch) [S] | Uncategorised, transfers, check later | No progress, no "confirm auto" step |
| Exclude from spending | Hide from budget/cash flow (Monarch) [S] | Only transfers are excluded | **Missing** |
| Undo | Common [K] | None | **Missing** |
| Category drill-down | Tap category for its history and trend [K] | Donut and legend only | **Missing** |
| Visual identity | Icons/logos, charts, motion [K] | Letter avatars, colour dots, donut, sparkline | Plain |

---

## 3. Flaws in All Banks

### Categorising
1. **No suggestions.** Every uncategorised item starts from a blank picker, even if you've categorised that merchant before.
2. **One at a time.** There's no multi-select, so clearing a backlog is slow.
3. **Too many steps for the common case.** Open a sheet, scroll past details, choose, tick the rule, then Save.
4. **Silent side effects.** Saving a rule re-categorises history with no "applied to 14 other transactions" message and no preview of which ones.
5. **Auto results look final.** Items put in "Other" or mapped from the bank's label never get a second look, so the Review count can look healthy while categories are wrong.
6. **No "why".** You can't see whether a category came from you, a rule, or the bank's label, and so can't fix the cause.
7. **No undo** for category changes, links, unlinks or merges.
8. **Merchant names are raw.** "TESCO STORES 4421", "TESCO EXPRESS" and "Tesco" are three things to rules and reports.

### Rules
9. **Rules only set a category.** They can't rename a merchant, mark an item reviewed, or exclude it from spending.
10. **The builder is technical.** "counterparty", "equals", "regex" mean nothing to most people.
11. **The default rule from "Remember for next time" is a guess** on the merchant text, with no suggestion of a safer pattern and no warning when it's too broad.
12. **No rule health:** no hit count, last-used date, or conflict warning (two rules that match the same item).
13. **No rule suggestions.** The app could notice you've fixed the same merchant three times.

### Look and feel
14. **No category icons.** Colour dots and initials look generic next to apps that use icons or logos.
15. **No category screen.** You can see the donut but can't tap Groceries to see its transactions, trend and top merchants.
16. **Little insight.** No month-by-month bars, no biggest changes, no top merchants, no custom date range.
17. **Charts and screens are static.** No animation or haptics, and sheets are long scrolling forms.
18. **Accessibility not checked:** screen-reader labels, large text.

---

## 4. Recommendations (in priority order)

### A. Make categorising fast
1. **Suggested categories at the top of the sheet:** the 3 most likely, from past transactions with the same merchant or similar text. One tap applies it and closes the sheet.
2. **Multi-select bulk actions** in Activity and Review: long-press to select, then Categorise, Mark reviewed, or Create rule from these.
3. **"Apply to similar?" step** after you categorise: shows "14 more transactions look like this" with a checklist (all ticked) and one confirm. It always reports what changed afterwards.
4. **Keep-going flow** in Review: "Next" after each save, and a progress bar ("12 left").
5. **Undo snackbar** for every change, plus a short "Recent changes" list.
6. **Show the source** on each transaction: "You chose this", "Rule: Tesco → Groceries", or "From your bank". Tapping the rule opens it.

### B. Better rules
7. **Plain-language builder:** "When **merchant** **contains** *Tesco*, and amount is *any*, then **set category** to *Groceries*." Hide regex behind an "Advanced" option.
8. **More actions:** rename merchant, set category, mark as reviewed, exclude from spending (for refunds or one-offs). Tags are optional later.
9. **Preview list**, not just a count, before saving; also "applies to future only" vs "also past".
10. **Rule health:** hit count, last used, conflicts, and a "no matches in 90 days" hint.
11. **Suggested rules:** after the same merchant is categorised three times, offer a rule once, not every time.
12. **Safer defaults:** use the cleaned merchant name, and warn when a pattern is very short or matches many different merchants.

### C. Merchants
13. **Merchant list** with total spent and count; rename and merge ("TESCO STORES 4421" → Tesco). Rules and reports then use the clean name.

### D. Look and feel
14. **Category icons:** a small curated icon/emoji set with a default per category; pick one in the category editor. Use them in rows, the donut legend and budgets.
15. **Category detail screen:** total, 6-month bar chart, top merchants, transactions.
16. **Insights on Overview:** month vs previous months, biggest increases, top merchants.
17. **Polish:** subtle animations on amounts and charts, light haptics on save/undo, shorter sheets (details collapsed behind "Details").
18. **Accessibility pass:** content descriptions, large-text testing, touch targets.
19. **Merchant logos** are possible because Endute supplies a brand domain, but fetching logos means contacting third-party servers for every merchant, which leaks your spending. Only add it as an opt-in setting.

### E. Housekeeping
20. **Exclude from spending** per transaction (also usable by rules) so reimbursements and one-offs don't distort totals.
21. **Optional category split** (one payment across two categories). Low priority, and only if you want it.

---

## 5. Suggested first slice

Highest impact for the least change: **A1 (suggestions), A3 (apply to similar with preview and feedback), A5 (undo), A6 (source shown), B7 and B8 (plain-language rules with rename and exclude), C13 (merchants), D14 (category icons)**.

## Sources

- Copilot Money: [Transaction category rules](https://changelog.copilot.money/log/transaction-category-rules), [Copilot dispatch](https://www.copilot.money/dispatch/transaction-category-rules)
- Monarch: [Creating transaction rules](https://help.monarch.com/hc/en-us/articles/360048393372), [Transaction review](https://monarchmoney.com/whats-new/transaction-review), [Tips and tricks](https://help.monarch.com/hc/en-us/articles/28953573066260-Tips-Tricks-for-Using-Monarch)
- YNAB: [Categorizing transactions](https://support.ynab.com/en_us/categorizing-transactions-a-guide-HyRl60sks.md)
- QuickBooks: [Categorize and match bank transactions](https://dancingnumbers.com/?p=89387), [Intuit renaming rules](https://quickbooks.intuit.com/learn-support/en-us/help-article/bank-feeds/use-renaming-rules-bank-feeds/L7m7kwjDP_US_en_US)
- FreeAgent: [How does Guess explain your transactions?](https://support.freeagent.com/hc/en-gb/articles/115001586004), [Explain in bulk](https://support.freeagent.com/hc/en-gb/articles/4407182814354)
- Xero: [Reconcile bank transactions quickly](https://www.xero.com/accounting-software/bank-reconciliation/), [UK guide](https://marcandrews.com/?p=3583)

**Limits:** several sources are marketing pages, review sites or third-party guides, and some Monarch notes are from 2022. Apps change; test any behaviour you plan to copy.
