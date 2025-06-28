# analytics.py
import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('expenses.csv', names=['date', 'type', 'amount'], parse_dates=['date'])

# Pie chart by category
cat_totals = df.groupby('type')['amount'].sum()
plt.figure(figsize=(6,6))
cat_totals.plot.pie(autopct='%1.1f%%')
plt.title('Spending by Category')
plt.ylabel('')
plt.tight_layout()
plt.savefig('category_chart.png')
plt.close()

# Bar chart by month
df['month'] = df['date'].dt.to_period('M')
month_totals = df.groupby('month')['amount'].sum()
month_totals.plot.bar()
plt.title('Monthly Spending')
plt.xlabel('Month')
plt.ylabel('Amount')
plt.tight_layout()
plt.savefig('monthly_chart.png')
plt.close()