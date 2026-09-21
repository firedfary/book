package com.flowledger.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.flowledger.app.R
import com.flowledger.app.databinding.ActivityMainBinding
import com.flowledger.app.ui.dashboard.DashboardFragment
import com.flowledger.app.ui.dialogs.AddTransactionBottomSheet
import com.flowledger.app.ui.transactions.TransactionsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val dashboardFragment = DashboardFragment()
    private val transactionsFragment = TransactionsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupFab()

        // 默认显示资产看板
        if (savedInstanceState == null) {
            switchFragment(dashboardFragment)
        }
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    switchFragment(dashboardFragment)
                    true
                }
                R.id.nav_transactions -> {
                    switchFragment(transactionsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            val bottomSheet = AddTransactionBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddTransactionBottomSheet")
        }
    }
}
