# Expense Tracker

Expense Tracker is an Android app that lets you input expense items to a Google Sheet. It also includes a widget with a breakdown of the amount remaining for each expense category.

## Setup

### 1. Set up the Google Sheet
1a. Make a clone of the Google Sheet budget template *[TODO: publish a blank copy of the template]*  
1b. Go to the `File > Publish to the web` dialog.  
1c. Choose the `Form Responses` sheet usage Tab-separated values (.tsv). The link will be the *`Google Form URL`*.  
1d. Click `Publish`.

### 2. Set up the Script
2a. Go to `Tools > Script Editor`.  
2b. Copy the contents of `Code.gs` in there. (You may need to run setup() once (`Run > Run function > setup`))  
2c. Go to `Publish > Deploy as web app > Current web app URL` and get the link. This will be the *`Google Script URL`*.  
2d. Change "exec" to "dev" if you want it to run the latest, unpublished changes

### 3. Set up the app
3a. Build the app in Android Studio, install it on your device.  
3b. Enter the `Google Sheets URL`, `Google Form URL`, and your currency via the Settings activity.

## License
[GPL](https://www.gnu.org/licenses/gpl-3.0.html)
