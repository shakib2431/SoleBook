package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SoleBookDao {

    // Products & Variants
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    // Visual Fingerprints (Offline Visual Match)
    @Query("SELECT * FROM product_fingerprints")
    fun observeFingerprints(): Flow<List<ProductFingerprintEntity>>

    @Query("SELECT * FROM product_fingerprints")
    suspend fun getAllFingerprintsSync(): List<ProductFingerprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFingerprint(fp: ProductFingerprintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFingerprints(fps: List<ProductFingerprintEntity>)

    @Query("DELETE FROM product_fingerprints WHERE productId = :productId")
    suspend fun deleteFingerprintForProduct(productId: Long)

    @Query("DELETE FROM product_fingerprints")
    suspend fun clearFingerprints()

    @Query("SELECT * FROM product_variants ORDER BY id ASC")
    fun observeVariants(): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants")
    suspend fun getAllVariantsSync(): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    suspend fun getVariantsForProduct(productId: Long): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND size = :size LIMIT 1")
    suspend fun getVariant(productId: Long, size: String): ProductVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<ProductVariantEntity>)

    @Update
    suspend fun updateVariant(variant: ProductVariantEntity)

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteVariantsForProduct(productId: Long)

    // Sales & Sale Items
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    suspend fun getAllSalesSync(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Long): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<SaleEntity>): List<Long>

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSaleById(saleId: Long)

    @Query("SELECT * FROM sale_items")
    fun observeSaleItems(): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items")
    suspend fun getAllSaleItemsSync(): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsForSale(saleId: Long): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsForSale(saleId: Long)

    // Returns
    @Query("SELECT * FROM returns ORDER BY timestamp DESC")
    fun observeReturns(): Flow<List<ReturnEntity>>

    @Query("SELECT * FROM returns ORDER BY timestamp DESC")
    suspend fun getAllReturnsSync(): List<ReturnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(returnEntity: ReturnEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturns(returns: List<ReturnEntity>)

    // Purchases (Restocks)
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun observePurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    suspend fun getAllPurchasesSync(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<PurchaseEntity>)

    // Vendors & Vendor Payments
    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun observeVendors(): Flow<List<VendorEntity>>

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    suspend fun getAllVendorsSync(): List<VendorEntity>

    @Query("SELECT * FROM vendors WHERE id = :id LIMIT 1")
    suspend fun getVendorById(id: Long): VendorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: VendorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendors(vendors: List<VendorEntity>): List<Long>

    @Update
    suspend fun updateVendor(vendor: VendorEntity)

    @Query("DELETE FROM vendors WHERE id = :vendorId")
    suspend fun deleteVendorById(vendorId: Long)

    @Query("SELECT * FROM vendor_payments ORDER BY timestamp DESC")
    fun observeVendorPayments(): Flow<List<VendorPaymentEntity>>

    @Query("SELECT * FROM vendor_payments ORDER BY timestamp DESC")
    suspend fun getAllVendorPaymentsSync(): List<VendorPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorPayment(payment: VendorPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendorPayments(payments: List<VendorPaymentEntity>)

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Long)

    // Customers
    @Query("SELECT * FROM customers ORDER BY lastVisitAt DESC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY lastVisitAt DESC")
    suspend fun getAllCustomersSync(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>): List<Long>

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    // Segments, Templates, Campaigns
    @Query("SELECT * FROM segments ORDER BY id ASC")
    fun observeSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments")
    suspend fun getAllSegmentsSync(): List<SegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("SELECT * FROM message_templates ORDER BY id ASC")
    fun observeTemplates(): Flow<List<MessageTemplateEntity>>

    @Query("SELECT * FROM message_templates")
    suspend fun getAllTemplatesSync(): List<MessageTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<MessageTemplateEntity>)

    @Query("SELECT * FROM campaigns ORDER BY timestamp DESC")
    fun observeCampaignLogs(): Flow<List<CampaignLogEntity>>

    @Query("SELECT * FROM campaigns ORDER BY timestamp DESC")
    suspend fun getAllCampaignLogsSync(): List<CampaignLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaignLog(log: CampaignLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaignLogs(logs: List<CampaignLogEntity>)

    // Settings
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): ShopSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ShopSettingsEntity)

    // Clear tables for reset / restore
    @Query("DELETE FROM sale_items")
    suspend fun clearSaleItems()

    @Query("DELETE FROM returns")
    suspend fun clearReturns()

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM purchases")
    suspend fun clearPurchases()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Query("DELETE FROM product_variants")
    suspend fun clearVariants()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM campaigns")
    suspend fun clearCampaigns()

    @Query("DELETE FROM segments")
    suspend fun clearSegments()

    // Priority Notice States
    @Query("SELECT * FROM notice_states")
    fun observeNoticeStates(): Flow<List<NoticeStateEntity>>

    @Query("SELECT * FROM notice_states")
    suspend fun getAllNoticeStatesSync(): List<NoticeStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNoticeState(state: NoticeStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNoticeStates(states: List<NoticeStateEntity>)

    @Query("DELETE FROM notice_states")
    suspend fun clearNoticeStates()

    @Query("DELETE FROM vendors")
    suspend fun clearVendors()

    @Query("DELETE FROM vendor_payments")
    suspend fun clearVendorPayments()

    @Query("DELETE FROM message_templates")
    suspend fun clearTemplates()
}
