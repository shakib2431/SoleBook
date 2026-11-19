package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WholesaleDao {

    // Raw Materials Rate Card & Optional Stock
    @Query("SELECT * FROM raw_materials ORDER BY category ASC, name ASC")
    fun observeRawMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials ORDER BY category ASC, name ASC")
    suspend fun getAllRawMaterialsSync(): List<RawMaterialEntity>

    @Query("SELECT * FROM raw_materials WHERE id = :id LIMIT 1")
    suspend fun getRawMaterialById(id: Long): RawMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterials(materials: List<RawMaterialEntity>): List<Long>

    @Update
    suspend fun updateRawMaterial(material: RawMaterialEntity)

    @Query("DELETE FROM raw_materials WHERE id = :id")
    suspend fun deleteRawMaterialById(id: Long)

    @Query("DELETE FROM raw_materials")
    suspend fun clearRawMaterials()

    // BOM Templates & Rules
    @Query("SELECT * FROM bom_templates ORDER BY id ASC")
    fun observeBomTemplates(): Flow<List<BomTemplateEntity>>

    @Query("SELECT * FROM bom_templates ORDER BY id ASC")
    suspend fun getAllBomTemplatesSync(): List<BomTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBomTemplate(template: BomTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBomTemplates(templates: List<BomTemplateEntity>): List<Long>

    @Update
    suspend fun updateBomTemplate(template: BomTemplateEntity)

    @Query("DELETE FROM bom_templates WHERE id = :id")
    suspend fun deleteBomTemplateById(id: Long)

    @Query("DELETE FROM bom_templates")
    suspend fun clearBomTemplates()

    @Query("SELECT * FROM bom_material_rules ORDER BY id ASC")
    fun observeBomMaterialRules(): Flow<List<BomMaterialRuleEntity>>

    @Query("SELECT * FROM bom_material_rules ORDER BY id ASC")
    suspend fun getAllBomMaterialRulesSync(): List<BomMaterialRuleEntity>

    @Query("SELECT * FROM bom_material_rules WHERE templateId = :templateId ORDER BY id ASC")
    suspend fun getRulesForTemplate(templateId: Long): List<BomMaterialRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBomRule(rule: BomMaterialRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBomRules(rules: List<BomMaterialRuleEntity>)

    @Query("DELETE FROM bom_material_rules WHERE templateId = :templateId")
    suspend fun deleteRulesForTemplate(templateId: Long)

    @Query("DELETE FROM bom_material_rules")
    suspend fun clearBomRules()

    // Wholesale Quotes & Quote Lines
    @Query("SELECT * FROM wholesale_quotes ORDER BY updatedAt DESC")
    fun observeWholesaleQuotes(): Flow<List<WholesaleQuoteEntity>>

    @Query("SELECT * FROM wholesale_quotes ORDER BY updatedAt DESC")
    suspend fun getAllWholesaleQuotesSync(): List<WholesaleQuoteEntity>

    @Query("SELECT * FROM wholesale_quotes WHERE id = :id LIMIT 1")
    suspend fun getWholesaleQuoteById(id: Long): WholesaleQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWholesaleQuote(quote: WholesaleQuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWholesaleQuotes(quotes: List<WholesaleQuoteEntity>)

    @Update
    suspend fun updateWholesaleQuote(quote: WholesaleQuoteEntity)

    @Query("DELETE FROM wholesale_quotes WHERE id = :id")
    suspend fun deleteWholesaleQuoteById(id: Long)

    @Query("DELETE FROM wholesale_quotes")
    suspend fun clearWholesaleQuotes()

    @Query("SELECT * FROM wholesale_quote_lines ORDER BY id ASC")
    fun observeAllQuoteLines(): Flow<List<WholesaleQuoteLineEntity>>

    @Query("SELECT * FROM wholesale_quote_lines ORDER BY id ASC")
    suspend fun getAllQuoteLinesSync(): List<WholesaleQuoteLineEntity>

    @Query("SELECT * FROM wholesale_quote_lines WHERE quoteId = :quoteId ORDER BY id ASC")
    suspend fun getLinesForQuote(quoteId: Long): List<WholesaleQuoteLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuoteLines(lines: List<WholesaleQuoteLineEntity>)

    @Query("DELETE FROM wholesale_quote_lines WHERE quoteId = :quoteId")
    suspend fun deleteLinesForQuote(quoteId: Long)

    @Query("DELETE FROM wholesale_quote_lines")
    suspend fun clearQuoteLines()
}
