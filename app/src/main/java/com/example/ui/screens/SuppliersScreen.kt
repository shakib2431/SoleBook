package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CanvasWarmWhite
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextCharcoal
import com.example.ui.viewmodel.SoleBookUiState
import com.example.ui.viewmodel.SoleBookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    state: SoleBookUiState,
    viewModel: SoleBookViewModel,
    onOpenVendorDetail: (Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = CanvasWarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("Suppliers", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = TextCharcoal
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasWarmWhite)
                .padding(innerPadding)
                .padding(top = 12.dp)
        ) {
            VendorsSectionContent(
                state = state,
                viewModel = viewModel,
                onOpenVendorDetail = onOpenVendorDetail
            )
        }
    }
}
