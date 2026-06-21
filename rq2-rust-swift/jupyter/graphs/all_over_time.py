import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import seaborn as sns
import pandas as pd
import textwrap

def wrap_labels(ax, width, break_long_words=False):
    labels = []
    for label in ax.get_xticklabels():
        text = label.get_text()
        wrapped_text = textwrap.fill(text, width=width, break_long_words=break_long_words)
        labels.append(wrapped_text)
    ax.set_xticklabels(labels)

def create_domain_comparison(data, path):
    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(10, 4))
    ax = sns.heatmap(
        data=data.pivot(index='project_name', columns='mapped_category_domain', values='proposal_count_normalized'),
        annot=True,
        fmt=".2f",
        cmap="YlGnBu",
        cbar_kws={'label': 'Proportion of Proposals'},
        linewidths=.5
    )
    ax.set_xlabel("Proposal Domains", fontsize=14, weight='bold')
    ax.set_ylabel("Project Name", fontsize=14, weight='bold')

    plt.xticks(rotation=0, ha='center', fontsize=12)
    plt.yticks(rotation=0, ha='right', fontsize=12)
    wrap_labels(ax, width=16)

    plt.tight_layout()
    plt.savefig(f'images/{path}_domain_comparison.svg', format='svg', bbox_inches='tight')
    plt.close()

def create_category_comparison(data, category_order, path):
    data['proposal_count_normalized'] = data['proposal_count_normalized'] * 100
    data['assigned_category'] = pd.Categorical(data['assigned_category'], categories=category_order, ordered=True)

    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(10, 5.5))
    ax = sns.heatmap(
        data=data.pivot(index='project_name', columns='assigned_category', values='proposal_count_normalized'),
        annot=True,
        fmt=".2f",
        cmap="YlGnBu",
        cbar_kws={'label': 'Percentage of Proposals'},
        linewidths=.5
    )

    ax.set_xlabel("Proposal Categories", fontsize=14, weight='bold')
    ax.set_ylabel("Project Name", fontsize=14, weight='bold')

    plt.xticks(rotation=30, ha='right', fontsize=12)
    plt.yticks(rotation=0, ha='right', fontsize=12)

    plt.tight_layout()
    plt.savefig(f'images/{path}_category_comparison.svg', format='svg', bbox_inches='tight')
    plt.close()

def create_grouped_bar_chart(df1, df2, category_order, path):
    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(12, 8))
    
    # Create a new column to distinguish between the two datasets
    df1['source'] = 'NLI Zero-Shot (bart-large-mnli)'
    df2['source'] = 'Generative LLM (Gemma 4 26B A4B Q4 0)'
    combined_df = pd.concat([df1, df2])
    
    ax = sns.barplot(
        data=combined_df,
        x='assigned_category',
        y='average_percentage',
        hue='source',
        palette='Set2',
        order=category_order
    )
    
    ax.set_xlabel("Proposal Categories", fontsize=18, weight='bold')
    ax.set_xticklabels(ax.get_xticklabels(), rotation=40, ha='right', fontsize=14)
    ax.set_yticklabels(ax.get_yticklabels(), fontsize=14)
    ax.set_ylabel("Distribution of Proposals (%)", fontsize=18, weight='bold')
    
    plt.legend(title="Source", loc='upper left', fontsize=14, title_fontsize=16)
    plt.tight_layout()
    plt.savefig(f'images/{path}_grouped_bar_chart.svg', format='svg', bbox_inches='tight')
    plt.close()

def create_category_trends_over_time(data, category_order, path):
    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(12, 8))
    
    ax = sns.lineplot(
        data=data,
        x='year',
        y='average_percentage',
        hue='assigned_category',
        marker='o',
        linewidth=2.5,
        palette='Set2',
        hue_order=category_order
    )
    
    ax.set_xlabel("Year", fontsize=20, weight='bold')
    ax.set_ylabel("Distribution of Proposals (%)", fontsize=20, weight='bold')
    
    ax.xaxis.set_major_locator(ticker.MaxNLocator(nbins=8, integer=True))
    
    plt.setp(ax.get_xticklabels(), rotation=30, horizontalalignment='right', fontsize=18)
    plt.setp(ax.get_yticklabels(), fontsize=18)
    
    plt.legend(title="Proposal Categories", loc='upper left')
    plt.tight_layout()
    plt.savefig(f'images/{path}_category_trends_over_time.svg', format='svg', bbox_inches='tight')
    plt.close()

def create_category_distribution_graph(data, category_order, project, path):
    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(12, 8))

    g = sns.lineplot(
        data=data,
        x='year',
        y='proposal_count',
        hue='assigned_category',
        marker='o',
        linewidth=2.5,
        palette='Set2',
        hue_order=category_order
    )

    g.set_xlabel("Year", fontsize=20, weight='bold')
    g.set_ylabel("Proposals", fontsize=20, weight='bold')

    plt.setp(g.get_xticklabels(), rotation=30, horizontalalignment='right', fontsize=18)
    plt.setp(g.get_yticklabels(), fontsize=18)
    plt.legend(title="Proposal Categories", loc='upper left')

    plt.tight_layout()
    plt.savefig(f'images/{path}_category_distribution_yearly_{project}.svg', format='svg', bbox_inches='tight')
    plt.close()