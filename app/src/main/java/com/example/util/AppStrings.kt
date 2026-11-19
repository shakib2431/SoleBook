package com.example.util

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)"),
    TAMIL("ta", "தமிழ் (Tamil)")
}

data class AppStrings(
    val tabHome: String,
    val tabStock: String,
    val tabSell: String,
    val tabMoney: String,
    val tabMore: String,
    val tabCustomers: String,
    val tabInsights: String,
    val searchProductsHint: String,
    val soldButton: String,
    val addToBill: String,
    val doneButton: String,
    val skipCustomer: String,
    val customerNudge: String,
    val sellingAtLossWarning: String,
    val newStockArrived: String,
    val todayLabel: String,
    val weekLabel: String,
    val monthLabel: String,
    val customLabel: String,
    val revenueLabel: String,
    val cogsLabel: String,
    val grossProfitLabel: String,
    val expensesLabel: String,
    val netProfitLabel: String,
    val settingsTitle: String
)

object StringsRegistry {
    private val english = AppStrings(
        tabHome = "Home",
        tabStock = "Stock",
        tabSell = "Sell",
        tabMoney = "Money",
        tabMore = "More",
        tabCustomers = "Customers",
        tabInsights = "Insights",
        searchProductsHint = "Search product name, SKU, or brand...",
        soldButton = "Sold",
        addToBill = "Add to Bill",
        doneButton = "Done",
        skipCustomer = "Skip",
        customerNudge = "Optional: link customer phone for receipts & offers",
        sellingAtLossWarning = "Selling price is below cost price",
        newStockArrived = "New Stock Arrived",
        todayLabel = "Today",
        weekLabel = "Week",
        monthLabel = "Month",
        customLabel = "Custom",
        revenueLabel = "Sales Revenue",
        cogsLabel = "Cost of Goods",
        grossProfitLabel = "Gross Profit",
        expensesLabel = "Expenses",
        netProfitLabel = "Net Profit",
        settingsTitle = "Business Settings"
    )

    private val hindi = AppStrings(
        tabHome = "होम",
        tabStock = "स्टॉक",
        tabSell = "बिक्री",
        tabMoney = "हिसाब",
        tabMore = "अधिक",
        tabCustomers = "ग्राहक",
        tabInsights = "सुझाव",
        searchProductsHint = "नाम, कोड या ब्रांड खोजें...",
        soldButton = "बिक गया",
        addToBill = "बिल में जोड़ें",
        doneButton = "पक्का करें",
        skipCustomer = "छोड़ें",
        customerNudge = "रसीद व ऑफ़र के लिए ग्राहक नंबर जोड़ें",
        sellingAtLossWarning = "बिक्री कीमत खरीद लागत से कम है",
        newStockArrived = "नया स्टॉक आया",
        todayLabel = "आज",
        weekLabel = "हफ़्ता",
        monthLabel = "महीना",
        customLabel = "तारीख चुनें",
        revenueLabel = "कुल बिक्री",
        cogsLabel = "खरीद लागत",
        grossProfitLabel = "कच्चा मुनाफ़ा",
        expensesLabel = "खर्चे",
        netProfitLabel = "शुद्ध मुनाफ़ा",
        settingsTitle = "व्यापार सेटिंग्स"
    )

    private val tamil = AppStrings(
        tabHome = "முகப்பு",
        tabStock = "சரக்கு",
        tabSell = "விற்பனை",
        tabMoney = "கணக்கு",
        tabMore = "மேலும்",
        tabCustomers = "வாடிக்கையாளர்",
        tabInsights = "ஆலோசனை",
        searchProductsHint = "பொருள் பெயர், SKU தேடுக...",
        soldButton = "விற்றது",
        addToBill = "பில்லில் சேர்",
        doneButton = "முடி",
        skipCustomer = "தவிர்",
        customerNudge = "ரசீது மற்றும் சலுகைகளுக்கு எண் சேர்க்கவும்",
        sellingAtLossWarning = "விற்பனை விலை அடக்க விலையை விட குறைவு",
        newStockArrived = "புதிய சரக்கு வந்தது",
        todayLabel = "இன்று",
        weekLabel = "வாரம்",
        monthLabel = "மாதம்",
        customLabel = "தேதி தேர்வு",
        revenueLabel = "விற்பனை வருவாய்",
        cogsLabel = "கொள்முதல் செலவு",
        grossProfitLabel = "மொத்த லாபம்",
        expensesLabel = "செலவுகள்",
        netProfitLabel = "நிகர லாபம்",
        settingsTitle = "வணிக அமைப்புகள்"
    )

    fun forCode(code: String): AppStrings = when (code) {
        AppLanguage.HINDI.code -> hindi
        AppLanguage.TAMIL.code -> tamil
        else -> english
    }
}
