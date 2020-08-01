# Expense Tracker

Expense Tracker is an Android app that lets you input expense items to a Google Sheet. It also includes a widget with a breakdown of the amount remaining for each expense category.

## How It Works

Expense Tracker provides a form to input information about an expense. On submission, the expense item is added as a row to the Data Sheet row. The sheet itself is set up to read these expenses and provide more insights. One way it accomplishes this is via the Monthly Summary sheet. It breaks down the spending in each category by month and compares them to the monthly targets. These insights can be easily viewed by using the app's Monthly Summary homescreen widget.

## Setup

### 1. Set up the Google Sheet
1a. Make a clone of the [Google Sheet budget template](https://docs.google.com/spreadsheets/d/1zZVv0_grvB7KWZND4ZegybTzZGcg5WUe3zMZtUN8t0s/edit?usp=sharing)

### 2. Set up the app
2a. Build the app in Android Studio, install it on your device.  
2b. Log in with your Google account  
2c. Set all the settings via the Settings activity:
- Expense Categories: a comma-separated list of categories for expenses. This controls what appears in the categories dropdown
- Monthly Summary Sheet Name: the name of the sheet containing the category breakdown by month
- Google Spreadsheet Id: unique Google spreadsheet identifier - `https://docs.google.com/spreadsheets/d/<spreadsheetId>/edit#gid=0`
- Data Sheet Name: the name of the sheet that expense items will be added to
- Currency: the default currency used in submissions if the currency field on the main form is left blank. Recommended to use a [ISO 4217](https://en.wikipedia.org/wiki/ISO_4217#Active_codes) currency code in case I decide to add automatic exchange rate lookup
- Exchange Rate: the current exchange rate to apply to expense amounts. Set to 1 for no change.

## License
[GPL](https://www.gnu.org/licenses/gpl-3.0.html)
