package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.CategoryDao
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getAllCategoriesList(): List<Category>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun addCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun ensureDefaultCategories()
}

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllCategoriesList(): List<Category> {
        return categoryDao.getAllCategoriesList().map { it.toDomain() }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)?.toDomain()
    }

    override suspend fun addCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toEntity())
    }

    override suspend fun ensureDefaultCategories() {
        if (categoryDao.getCount() == 0) {
            categoryDao.insertCategories(com.naman.rupeeflow.data.local.RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )

    private fun Category.toEntity() = CategoryEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )
}
