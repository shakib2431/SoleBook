package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OnboardingDao {

    // Barcode & SKU Lookups
    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(skuCode) = LOWER(:sku) LIMIT 1")
    suspend fun getProductBySku(sku: String): ProductEntity?

    @Query("SELECT * FROM product_variants WHERE barcode = :barcode LIMIT 1")
    suspend fun getVariantByBarcode(barcode: String): ProductVariantEntity?

    @Query("SELECT * FROM product_variants WHERE LOWER(variantSku) = LOWER(:sku) LIMIT 1")
    suspend fun getVariantBySku(sku: String): ProductVariantEntity?

    // Product Multi-Image Storage
    @Query("SELECT * FROM product_images ORDER BY isPrimary DESC, createdAt DESC")
    fun observeAllProductImages(): Flow<List<ProductImageEntity>>

    @Query("SELECT * FROM product_images WHERE productId = :productId ORDER BY isPrimary DESC, createdAt DESC")
    suspend fun getImagesForProduct(productId: Long): List<ProductImageEntity>

    @Query("SELECT * FROM product_images WHERE sha256Hash = :hash LIMIT 1")
    suspend fun findImageByHash(hash: String): ProductImageEntity?

    @Query("SELECT * FROM product_images")
    suspend fun getAllImagesSync(): List<ProductImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductImage(img: ProductImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductImages(imgs: List<ProductImageEntity>)

    @Query("DELETE FROM product_images")
    suspend fun clearProductImages()

    // Inventory Locations
    @Query("SELECT * FROM inventory_locations ORDER BY isDefault DESC, name ASC")
    fun observeLocations(): Flow<List<InventoryLocationEntity>>

    @Query("SELECT * FROM inventory_locations ORDER BY isDefault DESC, name ASC")
    suspend fun getAllLocationsSync(): List<InventoryLocationEntity>

    @Query("SELECT * FROM inventory_locations WHERE LOWER(code) = LOWER(:code) LIMIT 1")
    suspend fun getLocationByCode(code: String): InventoryLocationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(loc: InventoryLocationEntity): Long

    @Query("DELETE FROM inventory_locations")
    suspend fun clearLocations()

    // Inventory Movements (Idempotent Audit Ledger)
    @Query("SELECT * FROM inventory_movements ORDER BY timestamp DESC")
    fun observeMovements(): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM inventory_movements WHERE productId = :productId ORDER BY timestamp DESC")
    suspend fun getMovementsForProduct(productId: Long): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE movementKey = :key LIMIT 1")
    suspend fun getMovementByKey(key: String): InventoryMovementEntity?

    @Query("SELECT movementKey FROM inventory_movements WHERE sourceBatchId = :batchId")
    suspend fun getMovementKeysForBatch(batchId: Long): List<String>

    @Query("SELECT * FROM inventory_movements ORDER BY timestamp DESC")
    suspend fun getAllMovementsSync(): List<InventoryMovementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovementIgnore(movement: InventoryMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovementsIgnore(movements: List<InventoryMovementEntity>): List<Long>

    @Query("DELETE FROM inventory_movements")
    suspend fun clearMovements()

    // Common Import Staging Engine
    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC")
    fun observeImportBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC")
    suspend fun getAllImportBatchesSync(): List<ImportBatchEntity>

    @Query("SELECT * FROM import_batches WHERE id = :id LIMIT 1")
    suspend fun getImportBatchById(id: Long): ImportBatchEntity?

    @Query("SELECT * FROM import_batches WHERE fileHash = :hash ORDER BY createdAt DESC LIMIT 1")
    suspend fun getImportBatchByFileHash(hash: String): ImportBatchEntity?

    @Query("SELECT * FROM import_batches WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getImportBatchByIdempotencyKey(key: String): ImportBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportBatch(batch: ImportBatchEntity): Long

    @Update
    suspend fun updateImportBatch(batch: ImportBatchEntity)

    @Query("DELETE FROM import_batches WHERE id = :batchId")
    suspend fun deleteImportBatch(batchId: Long)

    @Query("SELECT * FROM staged_import_items WHERE batchId = :batchId ORDER BY rowNumber ASC, id ASC")
    fun observeStagedItemsForBatch(batchId: Long): Flow<List<StagedImportItemEntity>>

    @Query("SELECT * FROM staged_import_items WHERE batchId = :batchId ORDER BY rowNumber ASC, id ASC")
    suspend fun getStagedItemsForBatch(batchId: Long): List<StagedImportItemEntity>

    @Query("SELECT * FROM staged_import_items WHERE batchId = :batchId AND approvalStatus = :status ORDER BY rowNumber ASC, id ASC")
    suspend fun getStagedItemsByStatus(batchId: Long, status: String): List<StagedImportItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStagedItems(items: List<StagedImportItemEntity>): List<Long>

    @Update
    suspend fun updateStagedItem(item: StagedImportItemEntity)

    @Update
    suspend fun updateStagedItems(items: List<StagedImportItemEntity>)

    @Query("UPDATE staged_import_items SET approvalStatus = :status WHERE batchId = :batchId AND validationErrors = '' AND approvalStatus != 'COMMITTED'")
    suspend fun approveAllValidInBatch(batchId: Long, status: String = "APPROVED")

    @Query("DELETE FROM staged_import_items WHERE batchId = :batchId")
    suspend fun deleteStagedItemsForBatch(batchId: Long)

    @Query("DELETE FROM staged_import_items")
    suspend fun clearAllStagedItems()

    @Query("DELETE FROM import_batches")
    suspend fun clearAllImportBatches()

    // Physical Stock Count Sessions
    @Query("SELECT * FROM stock_count_sessions ORDER BY createdAt DESC")
    fun observeStockCountSessions(): Flow<List<StockCountSessionEntity>>

    @Query("SELECT * FROM stock_count_sessions WHERE id = :id LIMIT 1")
    suspend fun getStockCountSession(id: Long): StockCountSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockCountSession(session: StockCountSessionEntity): Long

    @Update
    suspend fun updateStockCountSession(session: StockCountSessionEntity)

    @Query("SELECT * FROM stock_count_lines WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeStockCountLines(sessionId: Long): Flow<List<StockCountLineEntity>>

    @Query("SELECT * FROM stock_count_lines WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getStockCountLines(sessionId: Long): List<StockCountLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockCountLines(lines: List<StockCountLineEntity>)

    @Update
    suspend fun updateStockCountLine(line: StockCountLineEntity)

    @Query("DELETE FROM stock_count_lines")
    suspend fun clearStockCountLines()

    @Query("DELETE FROM stock_count_sessions")
    suspend fun clearStockCountSessions()

    // Purchase Requests & Purchase Orders (separate from Inventory stock until Goods Received)
    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun observePurchaseOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    suspend fun getAllPurchaseOrdersSync(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun getPurchaseOrderById(id: Long): PurchaseOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseOrder(po: PurchaseOrderEntity): Long

    @Update
    suspend fun updatePurchaseOrder(po: PurchaseOrderEntity)

    @Query("DELETE FROM purchase_orders")
    suspend fun clearPurchaseOrders()
}
