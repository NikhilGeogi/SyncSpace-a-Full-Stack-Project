/* 
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/javascript.js to edit this template
 */

// ==========================================
// 全域變數與登入防護
// ==========================================
const CURRENT_USER_ID = document.body.dataset.userId;
const CURRENT_USERNAME = document.body.dataset.username;

let currentPageId = null;
let currentPermission = 'OWNER';
let currentContextMenuBlockId = null;
let targetParentPageId = null;
let sharingPageId = null;
let globalAllPages = []; 
let availablePageTemplates = [];
let selectedTemplateDefaultTitle = '';
const collapsedStates = new Map();

window.onload = function() {
    document.getElementById('workspace-name').innerText = CURRENT_USERNAME;
    document.getElementById('workspace-avatar').innerText = CURRENT_USERNAME.charAt(0).toUpperCase();
    // Set default header text in current language
    document.getElementById('page-header').innerText = i18n.t('default_header');
    loadSidebarPages();
    loadWorkspaceDashboard();
    loadDashboardPanels();
    loadPageTemplates();
    document.getElementById('page-tag-filter').addEventListener('change', renderSidebarPageList);
    document.getElementById('page-template-select').addEventListener('change', updateTemplateSelection);
    document.getElementById('add-page-tag-form').addEventListener('submit', submitPageTag);
    document.getElementById('markdown-import-input').addEventListener('change', importMarkdownFile);
    
    const isDarkMode = localStorage.getItem('DARK_MODE') === 'true';
    if (isDarkMode) {
        document.documentElement.classList.add('dark-mode');
        const toggle = document.getElementById('dark-mode-toggle');
        if(toggle) toggle.innerText = '☼';
    }

    // Restore saved sidebar width
    const savedSidebarWidth = localStorage.getItem('SIDEBAR_WIDTH');
    if (savedSidebarWidth) {
        document.getElementById('sidebar').style.width = Math.min(480, Math.max(220, Number(savedSidebarWidth))) + 'px';
    }

    // Sidebar resize drag logic
    const resizeHandle = document.getElementById('sidebar-resize-handle');
    const sidebar = document.getElementById('sidebar');
    if (resizeHandle && sidebar) {
        let isResizing = false;
        let startX = 0;
        let startWidth = 0;

        resizeHandle.addEventListener('mousedown', (e) => {
            isResizing = true;
            startX = e.clientX;
            startWidth = sidebar.offsetWidth;
            resizeHandle.classList.add('dragging');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            const delta = e.clientX - startX;
            const newWidth = Math.min(480, Math.max(220, startWidth + delta));
            sidebar.style.width = newWidth + 'px';
        });

        document.addEventListener('mouseup', () => {
            if (!isResizing) return;
            isResizing = false;
            resizeHandle.classList.remove('dragging');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            localStorage.setItem('SIDEBAR_WIDTH', sidebar.offsetWidth);
        });
    }
    
    // 拖曳放置邏輯
    const blockList = document.getElementById('block-list');
    blockList.addEventListener('dragover', e => {
        e.preventDefault(); 
        const dragging = document.querySelector('.dragging');
        if (!dragging) return;
        const afterElement = getDragAfterElement(blockList, e.clientY);
        if (afterElement == null) blockList.appendChild(dragging);
        else blockList.insertBefore(dragging, afterElement);
    });

};

function logout() {
    localStorage.removeItem('CURRENT_USER_ID');
    localStorage.removeItem('CURRENT_USERNAME');
    fetch('/api/users/logout', { method: 'POST' })
        .finally(() => { window.location.href = '/login'; });
}

function toggleDarkMode() {
    const isDark = document.documentElement.classList.toggle('dark-mode');
    localStorage.setItem('DARK_MODE', isDark);
    const toggle = document.getElementById('dark-mode-toggle');
    if (toggle) toggle.innerText = isDark ? '☼' : '☾';
}

// ==========================================
// 目錄與麵包屑邏輯
// ==========================================
async function loadSidebarPages() {
    try {
        const response = await fetch('/api/pages/mine/all');
        if (!response.ok) { console.error('Failed to load pages', response.status); return; }
        globalAllPages = await response.json(); 
        renderTagFilter();
        renderSidebarPageList();
        renderFavorites();
        
        if(currentPageId) {
            const header = document.getElementById('page-header');
            if (header.innerText !== i18n.t('default_header')) selectPage(currentPageId, header.innerText);
        }
    } catch (error) {
        console.error('Failed to load sidebar', error);
    }
}

function renderSidebarPageList() {
    const selectedTag = document.getElementById('page-tag-filter').value;
    const pages = selectedTag
        ? globalAllPages.filter(page => page.tagNames?.includes(selectedTag))
        : globalAllPages;
    const pageListContainer = document.getElementById('page-list');
    pageListContainer.replaceChildren();
    buildTreeNodes(pages, null, pageListContainer);
}

function renderTagFilter() {
    const filter = document.getElementById('page-tag-filter');
    const selected = filter.value;
    const names = [...new Set(globalAllPages.flatMap(page => page.tagNames || []))]
        .sort((first, second) => first.localeCompare(second));
    filter.replaceChildren(new Option('All pages', ''));
    names.forEach(name => filter.add(new Option(name, name)));
    if (names.includes(selected)) filter.value = selected;
}

function renderFavorites() {
    const section = document.getElementById('favorites-section');
    const list = document.getElementById('favorites-list');
    const favorites = globalAllPages.filter(page => page.favorite);
    list.replaceChildren();
    section.hidden = favorites.length === 0;
    favorites.forEach(page => {
        const row = document.createElement('li');
        row.className = 'favorite-page-row';
        const open = document.createElement('button');
        open.type = 'button';
        open.className = 'favorite-page-link';
        open.innerText = page.title;
        open.onclick = () => selectPage(page.id, page.title);
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'favorite-remove-btn';
        remove.innerText = '★';
        remove.title = 'Remove from pinned';
        remove.setAttribute('aria-label', `${remove.title}: ${page.title}`);
        remove.onclick = () => toggleFavorite(page.id, false);
        row.append(open, remove);
        list.appendChild(row);
    });
}

async function toggleFavorite(pageId, favorite) {
    try {
        const response = await fetch(`/api/pages/${pageId}/favorite`, { method: favorite ? 'POST' : 'DELETE' });
        if (!response.ok) throw new Error(`Favorite update failed: ${response.status}`);
        await loadSidebarPages();
    } catch (error) {
        console.error('Failed to update favorite', error);
        alert(i18n.t('server_error'));
    }
}

async function loadWorkspaceDashboard() {
    try {
        const response = await fetch('/api/pages/mine/recent');
        if (!response.ok) throw new Error(`Recent pages request failed: ${response.status}`);
        const pages = await response.json();
        const container = document.getElementById('recent-pages');
        container.replaceChildren();
        if (pages.length === 0) {
            container.innerText = 'Pages you open will appear here for quick access.';
            container.classList.add('dashboard-empty');
        } else {
            container.classList.remove('dashboard-empty');
            pages.forEach(page => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'recent-page-card';
            const icon = document.createElement('span');
            icon.className = 'recent-page-icon';
            icon.innerText = '↗';
            const details = document.createElement('span');
            details.className = 'recent-page-details';
            const title = document.createElement('strong');
            title.innerText = page.title;
            const viewed = document.createElement('small');
            viewed.innerText = formatRecentDate(page.lastViewedAt);
            details.append(title, viewed);
            button.append(icon, details);
            button.onclick = () => selectPage(page.id, page.title);
            container.appendChild(button);
            });
        }
    } catch (error) {
        console.error('Failed to load recently viewed pages', error);
        document.getElementById('recent-pages').innerText = i18n.t('server_error');
    }
}

async function loadDashboardPanels() {
    try {
        const [statsResponse, dueResponse, boardResponse] = await Promise.all([
            fetch('/api/dashboard/stats'),
            fetch('/api/todos?filter=today').then(async today => {
                if (!today.ok) throw new Error(`Today's tasks request failed: ${today.status}`);
                const todayItems = await today.json();
                const overdueResponse = await fetch('/api/todos?filter=overdue');
                if (!overdueResponse.ok) throw new Error(`Overdue tasks request failed: ${overdueResponse.status}`);
                return [...await overdueResponse.json(), ...todayItems];
            }),
            fetch('/api/todos/board')
        ]);
        if (!statsResponse.ok || !boardResponse.ok) throw new Error('Dashboard data request failed');
        renderDashboardStats(await statsResponse.json());
        renderDueTodos(await dueResponse);
        renderTodoBoard(await boardResponse.json());
    } catch (error) {
        console.error('Failed to load dashboard stats and tasks', error);
        document.getElementById('dashboard-stats').innerText = i18n.t('server_error');
        document.getElementById('due-todos').innerText = i18n.t('server_error');
        document.getElementById('todo-kanban').innerText = i18n.t('server_error');
    }
}

function renderDashboardStats(stats) {
    const container = document.getElementById('dashboard-stats');
    const chart = document.getElementById('dashboard-chart');
    const metrics = [
        { label: 'Pages', value: stats.pages, color: 'var(--primary-color)' },
        { label: 'Blocks', value: stats.blocks, color: '#7c6de8' },
        { label: 'Completed to-dos', value: stats.completedTodos, color: '#36a477' }
    ];
    container.replaceChildren();
    chart.replaceChildren();
    const maximum = Math.max(1, ...metrics.map(metric => metric.value));
    metrics.forEach(metric => {
        const card = document.createElement('div');
        card.className = 'stat-card';
        const value = document.createElement('strong');
        value.textContent = metric.value;
        const label = document.createElement('span');
        label.textContent = metric.label;
        card.append(value, label);
        container.appendChild(card);

        const bar = document.createElement('div');
        bar.className = 'stat-chart-row';
        const barLabel = document.createElement('span');
        barLabel.textContent = metric.label;
        const track = document.createElement('span');
        track.className = 'stat-chart-track';
        const fill = document.createElement('span');
        fill.className = 'stat-chart-fill';
        fill.style.width = `${Math.max(3, metric.value / maximum * 100)}%`;
        fill.style.backgroundColor = metric.color;
        track.appendChild(fill);
        const count = document.createElement('strong');
        count.textContent = metric.value;
        bar.append(barLabel, track, count);
        chart.appendChild(bar);
    });
}

function renderDueTodos(todos) {
    const container = document.getElementById('due-todos');
    document.getElementById('due-todos-count').textContent = `${todos.length} REMINDERS`;
    container.replaceChildren();
    if (todos.length === 0) {
        container.classList.add('dashboard-empty');
        container.textContent = 'Nothing due today. You are all caught up.';
        return;
    }
    container.classList.remove('dashboard-empty');
    todos.forEach(todo => {
        const row = document.createElement('button');
        row.type = 'button';
        row.className = `due-todo-row ${todo.overdue ? 'is-overdue' : ''}`;
        const text = document.createElement('span');
        text.textContent = todo.content || 'Untitled to-do';
        const page = document.createElement('small');
        page.textContent = `${todo.pageTitle} · ${todo.dueDate}`;
        row.append(text, page);
        row.onclick = () => selectPage(todo.pageId, todo.pageTitle);
        container.appendChild(row);
    });
}

function renderTodoBoard(todos) {
    const container = document.getElementById('todo-kanban');
    container.replaceChildren();
    [
        { key: 'todo', label: 'To do' },
        { key: 'doing', label: 'Doing' },
        { key: 'done', label: 'Done' }
    ].forEach(column => {
        const lane = document.createElement('section');
        lane.className = 'kanban-lane';
        const heading = document.createElement('h3');
        heading.textContent = `${column.label} · ${todos.filter(todo => todo.status === column.key).length}`;
        lane.appendChild(heading);
        todos.filter(todo => todo.status === column.key).forEach(todo => {
            const card = document.createElement('article');
            card.className = 'kanban-card';
            const open = document.createElement('button');
            open.type = 'button';
            open.className = 'kanban-card-open';
            open.textContent = todo.content || 'Untitled to-do';
            open.onclick = () => selectPage(todo.pageId, todo.pageTitle);
            const page = document.createElement('small');
            page.textContent = todo.pageTitle + (todo.dueDate ? ` · ${todo.dueDate}` : '');
            const status = document.createElement('select');
            status.className = 'kanban-status';
            status.setAttribute('aria-label', `Status for ${todo.content || 'to-do'}`);
            [['todo', 'To do'], ['doing', 'Doing'], ['done', 'Done']]
                .forEach(([value, label]) => status.add(new Option(label, value)));
            status.value = todo.status;
            status.disabled = !todo.canEdit;
            status.onchange = () => updateTodoStatus(todo, status.value);
            card.append(open, page, status);
            lane.appendChild(card);
        });
        container.appendChild(lane);
    });
}

async function updateTodoStatus(todo, status) {
    try {
        const response = await fetch(`/api/blocks/${todo.id}/todo-status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status })
        });
        if (!response.ok) throw new Error(`To-do status update failed: ${response.status}`);
        await loadDashboardPanels();
        if (currentPageId === todo.pageId) await selectPage(currentPageId, document.getElementById('page-header').innerText);
    } catch (error) {
        console.error('Failed to update to-do status', error);
        alert(i18n.t('server_error'));
    }
}

function formatRecentDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

async function loadPageTemplates() {
    try {
        const response = await fetch('/api/pages/templates');
        if (!response.ok) throw new Error(`Page templates request failed: ${response.status}`);
        availablePageTemplates = await response.json();
        const select = document.getElementById('page-template-select');
        availablePageTemplates.forEach(template => select.add(new Option(template.title, template.key)));
        renderDashboardTemplates();
        updateTemplateSelection();
    } catch (error) {
        console.error('Failed to load page templates', error);
    }
}

function renderDashboardTemplates() {
    const container = document.getElementById('dashboard-templates');
    container.replaceChildren();
    availablePageTemplates.forEach(template => {
        const card = document.createElement('button');
        card.type = 'button';
        card.className = 'dashboard-template-card';
        const icon = document.createElement('span');
        icon.className = 'dashboard-template-icon';
        icon.innerText = template.title === 'Meeting notes' ? 'M'
            : template.title === 'Weekly plan' ? 'W' : 'P';
        const title = document.createElement('strong');
        title.innerText = template.title;
        const description = document.createElement('small');
        description.innerText = template.description;
        const arrow = document.createElement('span');
        arrow.className = 'dashboard-template-arrow';
        arrow.innerText = '↗';
        card.append(icon, title, description, arrow);
        card.onclick = () => openModal(null, template.key);
        container.appendChild(card);
    });
}

function updateTemplateSelection() {
    const select = document.getElementById('page-template-select');
    const template = availablePageTemplates.find(item => item.key === select.value);
    const titleInput = document.getElementById('new-page-title');
    if (selectedTemplateDefaultTitle && titleInput.value === selectedTemplateDefaultTitle) {
        titleInput.value = '';
    }
    selectedTemplateDefaultTitle = template?.title || '';
    if (template) {
        if (!titleInput.value.trim()) titleInput.value = template.title;
        document.getElementById('page-template-description').innerText = template.description;
    } else {
        document.getElementById('page-template-description').innerText = 'A clean page with a single text line.';
    }
}

async function searchPages() {
    const query = document.getElementById('page-search-input').value.trim();
    const resultsPanel = document.getElementById('search-results');
    if (!query) {
        resultsPanel.style.display = 'none';
        resultsPanel.replaceChildren();
        return;
    }
    resultsPanel.style.display = 'block';
    resultsPanel.replaceChildren();

    try {
        const response = await fetch(`/api/pages/search?q=${encodeURIComponent(query)}`);
        if (!response.ok) throw new Error(`Search failed: ${response.status}`);
        const pages = await response.json();
        if (pages.length === 0) {
            resultsPanel.innerText = i18n.t('search_empty');
            return;
        }
        pages.forEach(page => {
            const result = document.createElement('button');
            result.type = 'button';
            result.className = 'feature-panel-item';
            result.innerText = page.title;
            result.onclick = () => selectPage(page.id, page.title);
            resultsPanel.appendChild(result);
        });
    } catch (error) {
        console.error('Failed to search pages', error);
        resultsPanel.innerText = i18n.t('server_error');
    }
}

function toggleTrash() {
    const panel = document.getElementById('trash-panel');
    const toggle = document.getElementById('trash-toggle-btn');
    const isOpen = panel.style.display === 'none';
    panel.style.display = isOpen ? 'block' : 'none';
    toggle.setAttribute('aria-expanded', String(isOpen));
    if (isOpen) loadTrash();
}

async function loadTrash() {
    const trashList = document.getElementById('trash-list');
    trashList.replaceChildren();
    try {
        const response = await fetch('/api/pages/trash');
        if (!response.ok) throw new Error(`Trash request failed: ${response.status}`);
        const pages = await response.json();
        if (pages.length === 0) {
            trashList.innerText = i18n.t('trash_empty');
            return;
        }
        pages.forEach(page => {
            const row = document.createElement('div');
            row.className = 'feature-panel-item';
            const title = document.createElement('span');
            title.innerText = page.title;
            const restore = document.createElement('button');
            restore.type = 'button';
            restore.innerText = i18n.t('restore_btn');
            restore.onclick = () => restorePage(page.id);
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.innerText = i18n.t('permanently_delete_btn');
            remove.onclick = () => permanentlyDeletePage(page.id);
            row.append(title, restore, remove);
            trashList.appendChild(row);
        });
    } catch (error) {
        console.error('Failed to load trash', error);
        trashList.innerText = i18n.t('server_error');
    }
}

async function restorePage(pageId) {
    try {
        const response = await fetch(`/api/pages/${pageId}/restore`, { method: 'POST' });
        if (!response.ok) throw new Error(`Restore failed: ${response.status}`);
        await Promise.all([loadTrash(), loadSidebarPages(), loadDashboardPanels()]);
    } catch (error) {
        console.error('Failed to restore page', error);
        alert(i18n.t('server_error'));
    }
}

async function permanentlyDeletePage(pageId) {
    if (!confirm(i18n.t('permanent_delete_confirm'))) return;
    try {
        const response = await fetch(`/api/pages/trash/${pageId}`, { method: 'DELETE' });
        if (!response.ok) throw new Error(`Permanent delete failed: ${response.status}`);
        await Promise.all([loadTrash(), loadDashboardPanels()]);
    } catch (error) {
        console.error('Failed to permanently delete page', error);
        alert(i18n.t('server_error'));
    }
}

function isPageWithinSubtree(pageId, rootId) {
    let page = globalAllPages.find(item => item.id == pageId);
    while (page) {
        if (page.id == rootId) return true;
        page = globalAllPages.find(item => item.id == page.parentPageId);
    }
    return false;
}

function buildTreeNodes(allPages, parentId, targetContainer) {
    const currentLevelPages = allPages.filter(p => p.parentPageId == parentId
        || (parentId == null && p.parentPageId != null
            && !allPages.some(parent => parent.id === p.parentPageId)));
    if (currentLevelPages.length === 0) return;
    const ul = document.createElement('ul');
    ul.className = 'tree-node';

    currentLevelPages.forEach(page => {
        const li = document.createElement('li');
        const hasChildren = allPages.some(p => p.parentPageId === page.id);
        const isCollapsed = collapsedStates.get(page.id) || false;

        const itemContainer = document.createElement('div');
        itemContainer.className = `page-item-container ${page.id === currentPageId ? 'active' : ''}`;
        itemContainer.onclick = () => selectPage(page.id, page.title);

        const arrow = document.createElement('span');
        arrow.className = `toggle-arrow ${isCollapsed ? 'collapsed' : ''}`;
        arrow.innerText = hasChildren ? '⌄' : '';
        arrow.setAttribute('aria-hidden', 'true');
        arrow.onclick = (e) => { e.stopPropagation(); collapsedStates.set(page.id, !isCollapsed); loadSidebarPages(); };

        const meta = document.createElement('div');
        meta.className = 'page-meta';
        const pageIcon = document.createElement('span');
        pageIcon.className = 'page-icon';
        pageIcon.setAttribute('aria-hidden', 'true');
        const pageTitle = document.createElement('span');
        pageTitle.innerText = page.title;
        meta.append(pageIcon, pageTitle);

        const actionDiv = document.createElement('div');
        actionDiv.className = 'page-actions';
        const favoriteBtn = document.createElement('button');
        favoriteBtn.type = 'button';
        favoriteBtn.className = `page-favorite-btn ${page.favorite ? 'is-favorite' : ''}`;
        favoriteBtn.innerText = page.favorite ? '★' : '☆';
        favoriteBtn.title = page.favorite ? 'Remove from pinned' : 'Pin page';
        favoriteBtn.setAttribute('aria-label', `${favoriteBtn.title}: ${page.title}`);
        favoriteBtn.setAttribute('aria-pressed', String(Boolean(page.favorite)));
        favoriteBtn.onclick = (e) => {
            e.stopPropagation();
            toggleFavorite(page.id, !page.favorite);
        };
        actionDiv.appendChild(favoriteBtn);
        if (page.permission !== 'VIEWER') {
            const subAddBtn = document.createElement('button');
            subAddBtn.type = 'button';
            subAddBtn.className = 'sub-add-btn';
            subAddBtn.innerText = '+';
            subAddBtn.title = i18n.t('create_sub_page', page.id);
            subAddBtn.setAttribute('aria-label', subAddBtn.title);
            subAddBtn.onclick = (e) => { e.stopPropagation(); openModal(page.id); };
            actionDiv.appendChild(subAddBtn);
        }
        if (String(page.userId) === String(CURRENT_USER_ID)) {
            const shareBtn = document.createElement('button');
            shareBtn.type = 'button';
            shareBtn.className = 'page-action-btn';
            shareBtn.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.1 0l3-3A5 5 0 0 0 13 2.9l-1.7 1.7M14 11a5 5 0 0 0-7.1 0l-3 3A5 5 0 0 0 11 21.1l1.7-1.7"></path></svg>';
            shareBtn.title = i18n.t('share_page_title');
            shareBtn.setAttribute('aria-label', shareBtn.title);
            shareBtn.onclick = (e) => { e.stopPropagation(); openShareModal(page.id); };
            const delBtn = document.createElement('button');
            delBtn.type = 'button';
            delBtn.className = 'page-del-btn';
            delBtn.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"></path></svg>';
            delBtn.title = i18n.t('trash_btn');
            delBtn.setAttribute('aria-label', delBtn.title);
            delBtn.onclick = (e) => { e.stopPropagation(); deletePage(page.id); };
            actionDiv.append(shareBtn, delBtn);
        }
        meta.prepend(arrow); itemContainer.appendChild(meta); itemContainer.appendChild(actionDiv);
        li.appendChild(itemContainer);

        if (hasChildren && !isCollapsed) buildTreeNodes(allPages, page.id, li);
        ul.appendChild(li);
    });
    targetContainer.appendChild(ul);
}

function renderBreadcrumbs(pageId) {
    const container = document.getElementById('breadcrumbs');
    container.innerHTML = '';
    if (!pageId) return;
    const path = [];
    let currentId = pageId;
    while (currentId) {
        const page = globalAllPages.find(p => p.id == currentId);
        if (!page) break;
        path.push(page);
        currentId = page.parentPageId;
    }
    path.reverse();
    const wsSpan = document.createElement('button');
    wsSpan.type = 'button';
    wsSpan.className = 'breadcrumb-item';
    wsSpan.innerText = i18n.t('workspace_breadcrumb');
    wsSpan.onclick = returnToDashboard;
    container.appendChild(wsSpan);

    path.forEach(page => {
        const sep = document.createElement('span'); sep.className = 'breadcrumb-separator'; sep.innerText = '/';
        container.appendChild(sep);
        const item = document.createElement('span'); item.className = 'breadcrumb-item'; item.innerText = page.title;
        item.onclick = () => selectPage(page.id, page.title);
        container.appendChild(item);
    });
}

function returnToDashboard() {
    currentPageId = null;
    currentPermission = 'OWNER';
    document.querySelectorAll('.page-item-container').forEach(item => item.classList.remove('active'));
    document.getElementById('main-content').classList.remove('page-open');
    document.getElementById('page-header').innerText = i18n.t('default_header');
    document.getElementById('breadcrumbs').replaceChildren();
    document.getElementById('block-list').replaceChildren();
    document.getElementById('page-tags-section').hidden = true;
    document.getElementById('backlinks-panel').hidden = true;
    document.getElementById('page-tools').hidden = true;
    document.getElementById('public-link-panel').hidden = true;
    document.getElementById('activity-panel').hidden = true;
    loadWorkspaceDashboard();
    loadDashboardPanels();
}

// ==========================================
// 頁面 CRUD 控制
// ==========================================
async function updateCurrentPageTitle() {
    if (!currentPageId || currentPermission === 'VIEWER') return;
    try {
        const newTitle = document.getElementById('page-header').innerText.trim();
        const res = await fetch(`/api/pages/${currentPageId}/title`, {
            method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: newTitle || i18n.t('untitled_placeholder') })
        });
        if (!res.ok) console.error('Failed to update title', res.status);
        loadSidebarPages();
    } catch (error) {
        console.error('Failed to send update title request', error);
    }
}

async function deletePage(id) {
    if(!confirm(i18n.t('delete_page_confirm'))) return;
    try {
        const res = await fetch(`/api/pages/${id}`, { method: 'DELETE' });
        if (!res.ok) { alert(i18n.t('delete_page_failed')); return; }
        if (isPageWithinSubtree(currentPageId, id)) {
            currentPageId = null;
            currentPermission = 'OWNER';
            document.getElementById('main-content').classList.remove('page-open');
            document.getElementById('page-header').innerText = i18n.t('default_header');
            document.getElementById('block-list').innerHTML = '';
            document.getElementById('breadcrumbs').innerHTML = '';
            document.getElementById('page-tags-section').hidden = true;
            document.getElementById('backlinks-panel').hidden = true;
            loadWorkspaceDashboard();
            loadDashboardPanels();
        }
        await loadSidebarPages();
        await loadDashboardPanels();
    } catch (error) {
        console.error('Failed to send delete page request', error);
        alert(i18n.t('server_error'));
    }
}

function openModal(parentId = null, templateKey = 'blank') {
    targetParentPageId = parentId && typeof parentId !== 'object' ? parseInt(parentId) : null;
    document.getElementById('page-modal-title').innerText = targetParentPageId
        ? i18n.t('create_sub_page', targetParentPageId) : i18n.t('create_top_page');
    document.getElementById('new-page-title').value = '';
    document.getElementById('page-template-select').value = templateKey;
    selectedTemplateDefaultTitle = '';
    updateTemplateSelection();
    document.getElementById('page-modal-overlay').style.display = 'flex';
    document.getElementById('new-page-title').focus(); 
}
function closeModal() {
    document.getElementById('page-modal-overlay').style.display = 'none';
    document.getElementById('new-page-title').value = '';
    document.getElementById('page-template-select').value = 'blank';
    selectedTemplateDefaultTitle = '';
    targetParentPageId = null;
}
function handleEnter(event) { if (event.key === 'Enter') submitNewPage(); }

function openShareModal(pageId) {
    sharingPageId = pageId;
    document.getElementById('share-modal-overlay').style.display = 'flex';
    document.getElementById('share-email').value = '';
    loadPageShares();
}

function closeShareModal() {
    document.getElementById('share-modal-overlay').style.display = 'none';
    sharingPageId = null;
}

function closeShareModalOnOverlay(event) {
    if (event.target.id === 'share-modal-overlay') closeShareModal();
}

async function loadPageShares() {
    const shareList = document.getElementById('share-list');
    shareList.replaceChildren();
    try {
        const response = await fetch(`/api/pages/${sharingPageId}/shares`);
        if (!response.ok) throw new Error(`Share list request failed: ${response.status}`);
        const shares = await response.json();
        shares.forEach(share => {
            const row = document.createElement('div');
            row.className = 'share-row';
            const details = document.createElement('span');
            details.innerText = `${share.username} (${share.email}) — ${share.permission}`;
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.innerText = i18n.t('remove_share_btn');
            remove.onclick = () => removePageShare(share.userId);
            row.append(details, remove);
            shareList.appendChild(row);
        });
    } catch (error) {
        console.error('Failed to load page shares', error);
        shareList.innerText = i18n.t('share_failed');
    }
}

async function submitShare() {
    const email = document.getElementById('share-email').value.trim();
    const permission = document.getElementById('share-permission').value;
    try {
        const response = await fetch(`/api/pages/${sharingPageId}/shares`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, permission })
        });
        if (!response.ok) throw new Error(`Sharing failed: ${response.status}`);
        await Promise.all([loadPageShares(), loadSidebarPages()]);
    } catch (error) {
        console.error('Failed to share page', error);
        alert(i18n.t('share_failed'));
    }
}

async function removePageShare(userId) {
    try {
        const response = await fetch(`/api/pages/${sharingPageId}/shares/${userId}`, { method: 'DELETE' });
        if (!response.ok) throw new Error(`Removing share failed: ${response.status}`);
        await Promise.all([loadPageShares(), loadSidebarPages()]);
    } catch (error) {
        console.error('Failed to remove page share', error);
        alert(i18n.t('share_failed'));
    }
}

async function submitNewPage() {
    const title = document.getElementById('new-page-title').value.trim();
    if (!title) return alert(i18n.t('title_required'));
    const templateKey = document.getElementById('page-template-select').value;
    try {
        const res = await fetch(templateKey === 'blank' ? '/api/pages' : '/api/pages/from-template', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(templateKey === 'blank'
                ? { title, userId: CURRENT_USER_ID, parentPageId: targetParentPageId }
                : { title, templateKey, parentPageId: targetParentPageId })
        });
        if (res.ok) {
            closeModal();
            await loadSidebarPages();
            await loadWorkspaceDashboard();
            await loadDashboardPanels();
        } else {
            throw new Error(`Page creation failed: ${res.status}`);
        }
    } catch (error) {
        console.error('Failed to send create page request', error);
        alert(i18n.t('server_error'));
    }
}

function renderPageTags(page) {
    const section = document.getElementById('page-tags-section');
    const tagsContainer = document.getElementById('page-tags');
    const form = document.getElementById('add-page-tag-form');
    const canEdit = page.permission !== 'VIEWER';
    section.hidden = false;
    form.hidden = !canEdit;
    tagsContainer.replaceChildren();
    (page.tagNames || []).forEach((name, index) => {
        const tag = document.createElement('span');
        tag.className = 'page-tag-chip';
        tag.innerText = name;
        if (canEdit) {
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.innerText = '×';
            remove.title = `Remove ${name}`;
            remove.setAttribute('aria-label', remove.title);
            remove.onclick = () => removePageTag(page.tagIds[index]);
            tag.appendChild(remove);
        }
        tagsContainer.appendChild(tag);
    });
}

async function submitPageTag(event) {
    event.preventDefault();
    if (!currentPageId) return;
    const input = document.getElementById('new-page-tag');
    const name = input.value.trim();
    if (!name) return;
    try {
        const response = await fetch(`/api/pages/${currentPageId}/tags`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        if (!response.ok) throw new Error(`Adding tag failed: ${response.status}`);
        input.value = '';
        await loadSidebarPages();
    } catch (error) {
        console.error('Failed to add page tag', error);
        alert(i18n.t('server_error'));
    }
}

async function removePageTag(tagId) {
    try {
        const response = await fetch(`/api/pages/${currentPageId}/tags/${tagId}`, { method: 'DELETE' });
        if (!response.ok) throw new Error(`Removing tag failed: ${response.status}`);
        await loadSidebarPages();
    } catch (error) {
        console.error('Failed to remove page tag', error);
        alert(i18n.t('server_error'));
    }
}

async function loadBacklinks(pageId) {
    const panel = document.getElementById('backlinks-panel');
    const list = document.getElementById('backlinks-list');
    panel.hidden = false;
    list.replaceChildren();
    try {
        const response = await fetch(`/api/pages/${pageId}/backlinks`);
        if (!response.ok) throw new Error(`Backlinks request failed: ${response.status}`);
        const pages = await response.json();
        if (pages.length === 0) {
            list.innerText = 'No pages link here yet.';
            list.classList.add('backlinks-empty');
            return;
        }
        list.classList.remove('backlinks-empty');
        pages.forEach(page => {
            const link = document.createElement('button');
            link.type = 'button';
            link.className = 'backlink-item';
            link.innerText = page.title;
            link.onclick = () => selectPage(page.id, page.title);
            list.appendChild(link);
        });
    } catch (error) {
        console.error('Failed to load page backlinks', error);
        list.innerText = i18n.t('server_error');
    }
}

function openMarkdownImport() {
    document.getElementById('markdown-import-input').click();
}

async function importMarkdownFile(event) {
    const file = event.target.files[0];
    event.target.value = '';
    if (!file || !currentPageId) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
        const response = await fetch(`/api/pages/${currentPageId}/import-markdown`, {
            method: 'POST',
            body: formData
        });
        if (!response.ok) throw new Error(`Markdown import failed: ${response.status} ${await response.text()}`);
        const result = await response.json();
        await selectPage(currentPageId, document.getElementById('page-header').innerText);
        await loadActivity(currentPageId);
        await loadDashboardPanels();
        alert(`Imported ${result.importedBlocks} blocks.`);
    } catch (error) {
        console.error('Failed to import Markdown', error);
        alert(error.message || i18n.t('server_error'));
    }
}

async function togglePublicLinkPanel() {
    const panel = document.getElementById('public-link-panel');
    panel.hidden = !panel.hidden;
    if (panel.hidden) return;
    await loadPublicLink();
}

async function loadPublicLink() {
    try {
        const response = await fetch(`/api/pages/${currentPageId}/public-link`);
        if (!response.ok) throw new Error(`Public-link status request failed: ${response.status}`);
        const link = await response.json();
        document.getElementById('public-link-url').value = link.url || '';
        document.getElementById('public-link-status').textContent = link.enabled
            ? 'Anyone with this link can view this page.'
            : 'This page does not have a public link.';
        document.getElementById('public-link-create').hidden = link.enabled;
        document.getElementById('public-link-copy').hidden = !link.enabled;
        document.getElementById('public-link-revoke').hidden = !link.enabled;
        document.getElementById('public-link-url').hidden = !link.enabled;
    } catch (error) {
        console.error('Failed to load public-link settings', error);
        document.getElementById('public-link-status').textContent = i18n.t('server_error');
    }
}

async function createPublicLink() {
    try {
        const response = await fetch(`/api/pages/${currentPageId}/public-link`, { method: 'POST' });
        if (!response.ok) throw new Error(`Public link creation failed: ${response.status}`);
        await loadPublicLink();
        await loadActivity(currentPageId);
    } catch (error) {
        console.error('Failed to create a public link', error);
        alert(i18n.t('server_error'));
    }
}

async function revokePublicLink() {
    if (!confirm('Revoke the public link? Anyone using the old link will lose access.')) return;
    try {
        const response = await fetch(`/api/pages/${currentPageId}/public-link`, { method: 'DELETE' });
        if (!response.ok) throw new Error(`Public link revocation failed: ${response.status}`);
        await loadPublicLink();
        await loadActivity(currentPageId);
    } catch (error) {
        console.error('Failed to revoke the public link', error);
        alert(i18n.t('server_error'));
    }
}

async function copyPublicLink() {
    const url = document.getElementById('public-link-url').value;
    if (!url) return;
    try {
        await navigator.clipboard.writeText(url);
        document.getElementById('public-link-status').textContent = 'Link copied to clipboard.';
    } catch (error) {
        console.error('Clipboard access failed', error);
        document.getElementById('public-link-url').select();
        document.getElementById('public-link-status').textContent = 'Select and copy the link above.';
    }
}

async function loadActivity(pageId) {
    const panel = document.getElementById('activity-panel');
    const list = document.getElementById('activity-list');
    panel.hidden = false;
    list.replaceChildren();
    try {
        const response = await fetch(`/api/pages/${pageId}/activity`);
        if (!response.ok) throw new Error(`Activity request failed: ${response.status}`);
        const events = await response.json();
        if (events.length === 0) {
            list.className = 'dashboard-empty';
            list.textContent = 'Page changes and comments will appear here.';
            return;
        }
        list.className = 'activity-list';
        events.forEach(event => {
            const row = document.createElement('div');
            row.className = 'activity-item';
            const details = document.createElement('span');
            details.textContent = `${event.username} ${event.details}`;
            const time = document.createElement('time');
            time.dateTime = event.createdAt;
            time.textContent = formatRecentDate(event.createdAt);
            row.append(details, time);
            list.appendChild(row);
        });
    } catch (error) {
        console.error('Failed to load page history', error);
        list.textContent = i18n.t('server_error');
    }
}

// ==========================================
// 區塊 (Block) 渲染與 Markdown 邏輯
// ==========================================
async function selectPage(pageId, pageTitle) {
    currentPageId = pageId;
    const page = globalAllPages.find(item => item.id === pageId);
    currentPermission = page ? page.permission : 'OWNER';
    document.getElementById('main-content').classList.add('page-open');
    document.getElementById('page-header').innerText = pageTitle;
    document.getElementById('page-header').contentEditable = currentPermission === 'VIEWER' ? 'false' : 'true';
    renderBreadcrumbs(pageId);
    if (page) renderPageTags(page);
    const pageTools = document.getElementById('page-tools');
    pageTools.hidden = currentPermission === 'VIEWER';
    document.getElementById('public-link-toggle').hidden = !page || Number(page.userId) !== Number(CURRENT_USER_ID);
    document.getElementById('public-link-panel').hidden = true;
    document.getElementById('activity-panel').hidden = false;
    
    document.querySelectorAll('.page-item-container').forEach(el => el.classList.remove('active'));

    try {
        const response = await fetch(`/api/blocks/page/${pageId}`);
        if (!response.ok) { console.error('Failed to load blocks', response.status); return; }
        var blocks = await response.json();
    } catch (error) {
        console.error('Failed to send load blocks request', error);
        return;
    }
    
    if (blocks.length === 0 && currentPermission !== 'VIEWER') {
        try {
            const res = await fetch('/api/blocks', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pageId: currentPageId, blockType: 'p', content: '', sortOrder: 1 })
            });
            // fetch 只有在網路層失敗時才會 reject，HTTP 500 對它而言是正常完成，
            // 所以一定要自己檢查 res.ok，否則下面會無限遞迴狂打伺服器。
            if (!res.ok) { console.error('Failed to create initial block', res.status); return; }
        } catch (error) {
            console.error('Failed to create initial block', error);
            return;
        }
        return selectPage(pageId, pageTitle);
    }

    const blockList = document.getElementById('block-list');
    blockList.innerHTML = '';

    blocks.forEach(block => {
        const container = document.createElement('div');
        container.className = 'block-container';
        container.draggable = currentPermission !== 'VIEWER';
        container.dataset.blockId = block.id; 

        const controls = document.createElement('div');
        controls.className = 'block-controls';
        if (currentPermission === 'VIEWER') controls.style.display = 'none';
        const addBtn = document.createElement('div');
        addBtn.className = 'add-btn'; addBtn.innerHTML = '＋';
        addBtn.onclick = async (e) => {
            await fetch('/api/blocks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pageId: currentPageId, blockType: 'p', content: '', sortOrder: 999 }) });
            const res = await fetch(`/api/blocks/page/${currentPageId}`);
            const updatedBlocks = await res.json();
            const newBlockId = updatedBlocks[updatedBlocks.length - 1].id;
            const currentIds = [...document.querySelectorAll('.block-container')].map(el => parseInt(el.dataset.blockId));
            const targetIndex = currentIds.indexOf(block.id);
            if (e && e.altKey) currentIds.splice(targetIndex, 0, newBlockId); 
            else currentIds.splice(targetIndex + 1, 0, newBlockId); 
            await fetch(`/api/blocks/page/${currentPageId}/reorder`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(currentIds) });
            selectPage(currentPageId, document.getElementById('page-header').innerText);
            loadDashboardPanels();
        };
        const dragHandle = document.createElement('button');
        dragHandle.type = 'button';
        dragHandle.className = 'drag-handle';
        dragHandle.innerText = '⋮⋮';
        dragHandle.title = 'Block options · type / in an empty line to change its type';
        dragHandle.setAttribute('aria-label', dragHandle.title);
        dragHandle.onclick = (e) => { e.stopPropagation(); openBlockContextMenu(e, block.id); };
        controls.appendChild(addBtn); controls.appendChild(dragHandle); container.appendChild(controls); 

        const contentDiv = document.createElement('div');
        contentDiv.className = `editable-block block-${block.blockType}`;
        
        if (block.blockType === 'todo') {
            contentDiv.classList.add('block-todo-wrapper');

            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.className = 'todo-checkbox';
            cb.disabled = currentPermission === 'VIEWER';
            
            // 讀取後端傳來的狀態
            cb.checked = block.isChecked || false; 

            const todoText = document.createElement('span');
            todoText.className = 'todo-text';
            todoText.contentEditable = 'true';
            if (currentPermission === 'VIEWER') todoText.contentEditable = 'false';
            todoText.innerText = block.content;
            if(cb.checked) todoText.classList.add('todo-checked');

            // 綁定打勾事件
            cb.onchange = async (e) => { 
                const isCheckedNow = e.target.checked;
                if(isCheckedNow) {
                    todoText.classList.add('todo-checked'); 
                } else {
                    todoText.classList.remove('todo-checked');
                }
                try {
                    const response = await fetch(`/api/blocks/${block.id}/check`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ isChecked: isCheckedNow })
                    });
                    if (!response.ok) throw new Error(`To-do update failed: ${response.status}`);
                    block.todoStatus = isCheckedNow ? 'done' : 'todo';
                    await loadDashboardPanels();
                } catch (error) {
                    console.error("Failed to save check status", error);
                    cb.checked = !isCheckedNow;
                    todoText.classList.toggle('todo-checked', cb.checked);
                    alert(i18n.t('server_error'));
                }
            };

            // 綁定文字編輯事件
            todoText.onblur = (e) => {
                const newText = e.target.innerText.trim();
                if (newText !== block.content) { updateBlockItem(block.id, newText); block.content = newText; }
            };
            todoText.onkeydown = async (e) => {
                if (openBlockPickerFromKeyboard(e, block.id, todoText)) return;
                if (e.key === 'Backspace' && e.target.innerText.trim() === '') { 
                    e.preventDefault(); 
                    deleteBlockItem(block.id); 
                }
                if (e.key === 'Enter' && !e.isComposing) { 
                    e.preventDefault(); 
                    const currentText = e.target.innerText.trim();
                    if (currentText !== block.content) {
                        await updateBlockItem(block.id, currentText);
                        block.content = currentText;
                    }
                    const addBtnRef = container.querySelector('.add-btn');
                    if (addBtnRef) addBtnRef.click();
                }
            };

            contentDiv.contentEditable = 'false';
            contentDiv.appendChild(cb);
            contentDiv.appendChild(todoText);

            const taskMeta = document.createElement('div');
            taskMeta.className = 'todo-meta';
            const dueInput = document.createElement('input');
            dueInput.type = 'date';
            dueInput.className = 'todo-due-date';
            dueInput.value = block.dueDate || '';
            dueInput.disabled = currentPermission === 'VIEWER';
            dueInput.setAttribute('aria-label', 'To-do due date');
            dueInput.title = 'Set due date';
            dueInput.onchange = async () => {
                try {
                    const response = await fetch(`/api/blocks/${block.id}/due-date`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ dueDate: dueInput.value || null })
                    });
                    if (!response.ok) throw new Error(`Due-date update failed: ${response.status}`);
                    const result = await response.json();
                    block.dueDate = result.dueDate;
                    block.overdueFlag = result.overdue;
                    dueInput.classList.toggle('is-overdue', result.overdue);
                    await loadDashboardPanels();
                } catch (error) {
                    console.error('Failed to update to-do due date', error);
                    alert(i18n.t('server_error'));
                    dueInput.value = block.dueDate || '';
                }
            };
            dueInput.classList.toggle('is-overdue', Boolean(block.overdueFlag));
            taskMeta.appendChild(dueInput);
            contentDiv.appendChild(taskMeta);
        }

        if (block.blockType === 'page') {
            contentDiv.classList.add('block-page'); contentDiv.contentEditable = "false"; 
            const linkedPageId = parseInt(block.content); 
            const linkedPage = globalAllPages.find(p => p.id === linkedPageId);
            const displayTitle = linkedPage ? linkedPage.title : i18n.t('deleted_page');
            const pageLinkIcon = document.createElement('span');
            pageLinkIcon.className = 'page-link-icon';
            pageLinkIcon.innerText = '📄';
            const pageLinkText = document.createElement('span');
            pageLinkText.className = 'page-link-text';
            pageLinkText.innerText = displayTitle;
            contentDiv.append(pageLinkIcon, pageLinkText);
            contentDiv.onclick = () => { if(linkedPage) selectPage(linkedPageId, displayTitle); };
        } else if (block.blockType !== 'todo') {
            contentDiv.contentEditable = currentPermission === 'VIEWER' ? "false" : "true";
            contentDiv.innerText = block.content;
            contentDiv.onblur = (e) => {
                const newText = e.target.innerText.trim();
                if (newText !== block.content) { updateBlockItem(block.id, newText); block.content = newText; }
            };

            contentDiv.onkeyup = async (e) => {
                if (e.key === ' ' || e.code === 'Space') {
                    const text = contentDiv.textContent.replace(/\u00A0/g, ' '); 
                    if (text === '# ' && block.blockType !== 'h1') {
                        e.preventDefault();
                        contentDiv.onblur = null; // 🛡️ 防線 1：拔掉失去焦點的存檔，防止存入 "# "
                        await updateBlockTypeDirectly(block.id, 'h1');
                    } else if (text === '[] ' && block.blockType !== 'todo') {
                        e.preventDefault();
                        contentDiv.onblur = null; // 🛡️ 防線 1：拔掉失去焦點的存檔
                        await updateBlockTypeDirectly(block.id, 'todo');
                    }
                }
            };

            // 🆕 鍵盤操作監聽 (修復版：加入 async/await 強制等存檔)
            contentDiv.onkeydown = async (e) => {
                if (openBlockPickerFromKeyboard(e, block.id, contentDiv)) return;
                if (e.key === 'Backspace' && e.target.innerText.trim() === '') { 
                    e.preventDefault(); 
                    deleteBlockItem(block.id); 
                }
                if (e.key === 'Enter' && !e.isComposing) { 
                    e.preventDefault(); 
                    
                    // 🛡️ 防線 2：在建立新行之前，先確認目前的字已經確實存入資料庫
                    const currentText = e.target.innerText.trim();
                    if (currentText !== block.content) {
                        await updateBlockItem(block.id, currentText); // 👈 加入 await 確保存檔完成
                        block.content = currentText;
                    }
                    
                    addBtn.click(); // 存檔完畢後，才觸發新增區塊
                }
            };
        }

        if (currentPermission !== 'VIEWER') {
            container.addEventListener('dragstart', () => container.classList.add('dragging'));
            container.addEventListener('dragend', () => { container.classList.remove('dragging'); saveNewBlockOrder(); });
            container.oncontextmenu = (e) => { e.preventDefault(); e.stopPropagation(); openBlockContextMenu(e, block.id); };
        }

        container.appendChild(contentDiv);
        container.appendChild(createBlockComments(block.id));
        blockList.appendChild(container);
    });
    await Promise.all([loadBacklinks(pageId), loadActivity(pageId)]);
}

function createBlockComments(blockId) {
    const details = document.createElement('details');
    details.className = 'block-comments';
    const heading = document.createElement('summary');
    heading.textContent = 'Comments';
    const content = document.createElement('div');
    content.className = 'block-comments-content';
    details.append(heading, content);
    details.addEventListener('toggle', () => {
        if (details.open && !details.dataset.loaded) {
            details.dataset.loaded = 'true';
            loadBlockComments(blockId, content);
        }
    });
    return details;
}

async function loadBlockComments(blockId, container) {
    container.replaceChildren();
    try {
        const response = await fetch(`/api/blocks/${blockId}/comments`);
        if (!response.ok) throw new Error(`Comments request failed: ${response.status}`);
        const comments = await response.json();
        comments.forEach(comment => container.appendChild(createCommentElement(comment, blockId)));
        const form = document.createElement('form');
        form.className = 'block-comment-form';
        const input = document.createElement('textarea');
        input.maxLength = 4000;
        input.required = true;
        input.placeholder = 'Write a comment…';
        const submit = document.createElement('button');
        submit.type = 'submit';
        submit.textContent = 'Comment';
        form.append(input, submit);
        form.onsubmit = async event => {
            event.preventDefault();
            const content = input.value.trim();
            if (!content) return;
            try {
                const result = await fetch(`/api/blocks/${blockId}/comments`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ content })
                });
                if (!result.ok) throw new Error(`Comment creation failed: ${result.status}`);
                const comment = await result.json();
                container.insertBefore(createCommentElement(comment, blockId), form);
                input.value = '';
                await loadActivity(currentPageId);
            } catch (error) {
                console.error('Failed to add block comment', error);
                alert(i18n.t('server_error'));
            }
        };
        container.appendChild(form);
    } catch (error) {
        console.error('Failed to load block comments', error);
        container.textContent = i18n.t('server_error');
    }
}

function createCommentElement(comment, blockId) {
    const row = document.createElement('article');
    row.className = 'block-comment';
    const heading = document.createElement('div');
    heading.className = 'block-comment-heading';
    const author = document.createElement('strong');
    author.textContent = comment.username;
    const time = document.createElement('time');
    time.textContent = formatRecentDate(comment.createdAt);
    heading.append(author, time);
    const body = document.createElement('p');
    body.textContent = comment.content;
    row.append(heading, body);
    const page = globalAllPages.find(item => Number(item.id) === Number(currentPageId));
    if (Number(comment.userId) === Number(CURRENT_USER_ID) ||
            (page && Number(page.userId) === Number(CURRENT_USER_ID))) {
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'comment-delete-btn';
        remove.textContent = 'Delete';
        remove.onclick = async () => {
            try {
                const response = await fetch(`/api/comments/${comment.id}`, { method: 'DELETE' });
                if (!response.ok) throw new Error(`Comment deletion failed: ${response.status}`);
                row.remove();
                await loadActivity(currentPageId);
            } catch (error) {
                console.error('Failed to delete block comment', error);
                alert(i18n.t('server_error'));
            }
        };
        row.appendChild(remove);
    }
    return row;
}

async function updateBlockTypeDirectly(blockId, newType) {
    try {
        await fetch(`/api/blocks/${blockId}/type`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: newType }) });
        await updateBlockItem(blockId, ""); // Clear # or []
        
        // 重新渲染畫面 (加上 await 確保渲染完成)
        await selectPage(currentPageId, document.getElementById('page-header').innerText);
        await loadDashboardPanels();
        
        // 重新把游標放回該區塊，讓使用者可以繼續打字
        setTimeout(() => {
            const blockContainer = document.querySelector(`.block-container[data-block-id="${blockId}"]`);
            if (!blockContainer) return;
            const focusTarget = blockContainer.querySelector('.todo-text') || blockContainer.querySelector('.editable-block');
            if (focusTarget) focusTarget.focus();
        }, 50);
    } catch (error) {
        console.error('Failed to update block type', error);
    }
}

// ==========================================
// 拖曳、菜單與其他控制 API
// ==========================================
async function updateBlockItem(blockId, newContent) {
    try {
        const res = await fetch(`/api/blocks/${blockId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: newContent }) });
        if (!res.ok) console.error('Failed to update block content', res.status);
        else await loadDashboardPanels();
    } catch (error) {
        console.error('Failed to send update block request', error);
    }
}
async function deleteBlockItem(blockId) {
    try {
        const res = await fetch(`/api/blocks/${blockId}`, { method: 'DELETE' });
        if (res.ok) {
            await selectPage(currentPageId, document.getElementById('page-header').innerText);
            await loadDashboardPanels();
        }
        else console.error('Failed to delete block', res.status);
    } catch (error) {
        console.error('Failed to send delete block request', error);
    }
}

function getDragAfterElement(container, y) {
    const draggableElements = [...container.querySelectorAll('.block-container:not(.dragging)')];
    return draggableElements.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) return { offset: offset, element: child };
        else return closest;
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

async function saveNewBlockOrder() {
    try {
        const blocks = document.querySelectorAll('.block-container');
        const newOrderIds = [];
        blocks.forEach(block => { if (block.dataset.blockId) newOrderIds.push(parseInt(block.dataset.blockId)); });
        const res = await fetch(`/api/blocks/page/${currentPageId}/reorder`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(newOrderIds) });
        if (!res.ok) console.error('Failed to update sort order', res.status);
    } catch (error) {
        console.error('Failed to send reorder request', error);
    }
}

function openBlockContextMenu(event, blockId) {
    currentContextMenuBlockId = blockId;
    const menu = document.getElementById('block-context-menu');
    const selectedBlock = document.querySelector(`.block-container[data-block-id="${blockId}"]`);
    if (selectedBlock) {
        document.querySelectorAll('.block-container').forEach(el => el.classList.remove('selected'));
        selectedBlock.classList.add('selected');
        const content = selectedBlock.querySelector('.editable-block');
        const blockType = ['p', 'h1', 'todo', 'page'].find(type => content.classList.contains(`block-${type}`)) || 'p';
        menu.querySelectorAll('.block-type-option').forEach(option => {
            const isCurrent = option.dataset.type === blockType;
            option.classList.toggle('is-current', isCurrent);
            option.setAttribute('aria-pressed', String(isCurrent));
        });
    }
    menu.style.display = 'flex';
    const margin = 8;
    const left = Math.min(event.clientX, window.innerWidth - menu.offsetWidth - margin);
    const top = Math.min(event.clientY, window.innerHeight - menu.offsetHeight - margin);
    menu.style.top = `${Math.max(margin, top)}px`;
    menu.style.left = `${Math.max(margin, left)}px`;
}

function openBlockPickerFromKeyboard(event, blockId, editableElement) {
    if (event.key !== '/' || event.isComposing || editableElement.innerText.trim() !== '') return false;
    event.preventDefault();
    const block = document.querySelector(`.block-container[data-block-id="${blockId}"]`);
    const bounds = block.getBoundingClientRect();
    openBlockContextMenu({ clientX: bounds.left + 44, clientY: bounds.top }, blockId);
    document.querySelector('#block-context-menu .block-type-option.is-current')?.focus();
    return true;
}

function closeBlockContextMenu() { document.getElementById('block-context-menu').style.display = 'none'; }

async function updateBlockTypeItem(newType) {
    try {
        const response = await fetch(`/api/blocks/${currentContextMenuBlockId}/type`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: newType })
        });
        if (!response.ok) throw new Error(`Updating block type failed: ${response.status}`);
        const blockId = currentContextMenuBlockId;
        closeBlockContextMenu();
        await selectPage(currentPageId, document.getElementById('page-header').innerText);
        await loadDashboardPanels();
        const block = document.querySelector(`.block-container[data-block-id="${blockId}"]`);
        const focusTarget = block?.querySelector('.todo-text, .editable-block[contenteditable="true"]');
        focusTarget?.focus();
    } catch (error) {
        console.error('Failed to update block type', error);
        closeBlockContextMenu();
    }
}

async function turnBlockIntoPage() {
    try {
        const blockEl = document.querySelector(`.block-container[data-block-id="${currentContextMenuBlockId}"] .editable-block`);
        let newTitle = blockEl ? blockEl.innerText.trim() : i18n.t('untitled_page');
        if (!newTitle || newTitle.includes(i18n.t('deleted_page'))) newTitle = i18n.t('untitled_page');
        
        // 使用後端回傳的新頁面 ID，而非從陣列最後一個元素猜測
        const createRes = await fetch('/api/pages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: newTitle, userId: CURRENT_USER_ID, parentPageId: currentPageId }) });
        if (!createRes.ok) { alert(i18n.t('create_page_failed')); return; }
        const createData = await createRes.json();
        const newPageId = createData.id;
        
        const typeResponse = await fetch(`/api/blocks/${currentContextMenuBlockId}/type`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: 'page' })
        });
        if (!typeResponse.ok) throw new Error(`Converting block to page failed: ${typeResponse.status}`);
        const contentResponse = await fetch(`/api/blocks/${currentContextMenuBlockId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: String(newPageId) })
        });
        if (!contentResponse.ok) throw new Error(`Linking page to block failed: ${contentResponse.status}`);
        closeBlockContextMenu();
        await loadSidebarPages();
        await selectPage(currentPageId, document.getElementById('page-header').innerText);
        await loadDashboardPanels();
    } catch (error) {
        console.error('Failed to convert block to page', error);
        closeBlockContextMenu();
    }
}

async function duplicateBlockItem() {
    try {
        const response = await fetch(`/api/blocks/page/${currentPageId}`);
        if (!response.ok) { closeBlockContextMenu(); return; }
        const blocks = await response.json();
        const blockToCopy = blocks.find(b => b.id === currentContextMenuBlockId);
        if (!blockToCopy) return closeBlockContextMenu();
        await fetch('/api/blocks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pageId: currentPageId, blockType: blockToCopy.blockType, content: blockToCopy.content, sortOrder: 999 }) });
        const res = await fetch(`/api/blocks/page/${currentPageId}`);
        const updatedBlocks = await res.json();
        const newBlockId = updatedBlocks[updatedBlocks.length - 1].id;
        const currentIds = [...document.querySelectorAll('.block-container')].map(el => parseInt(el.dataset.blockId));
        const targetIndex = currentIds.indexOf(currentContextMenuBlockId);
        currentIds.splice(targetIndex + 1, 0, newBlockId);
        await fetch(`/api/blocks/page/${currentPageId}/reorder`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(currentIds) });
        closeBlockContextMenu();
        await selectPage(currentPageId, document.getElementById('page-header').innerText);
        await loadDashboardPanels();
    } catch (error) {
        console.error('Failed to duplicate block', error);
        closeBlockContextMenu();
    }
}
function deleteBlockMenuItem() { closeBlockContextMenu(); deleteBlockItem(currentContextMenuBlockId); }

document.addEventListener('keydown', (e) => {
    const blockMenu = document.getElementById('block-context-menu');
    if (blockMenu.style.display !== 'none' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
        const options = [...blockMenu.querySelectorAll('.block-type-option')];
        const currentIndex = options.indexOf(document.activeElement);
        const direction = e.key === 'ArrowDown' ? 1 : -1;
        const nextIndex = currentIndex < 0
            ? (direction > 0 ? 0 : options.length - 1)
            : (currentIndex + direction + options.length) % options.length;
        options[nextIndex].focus();
        e.preventDefault();
        return;
    }
    if (e.key === 'Delete' && blockMenu.style.display === 'none'
            && !e.target.closest('[contenteditable="true"]')) {
        const selectedBlock = document.querySelector('.block-container.selected');
        if (selectedBlock) deleteBlockItem(selectedBlock.dataset.blockId); 
    }
    if (e.key === 'Escape') closeBlockContextMenu();
});
document.addEventListener('click', (e) => {
    if (!e.target.closest('#block-context-menu')) {
        document.querySelectorAll('.block-container').forEach(el => el.classList.remove('selected'));
        const menu = document.getElementById('block-context-menu');
        if (menu.style.display !== 'none') closeBlockContextMenu();
    }
});

// ==========================================
// 帳號設定懸浮視窗
// ==========================================
function openAccountSettings() {
    // Reset all fields and messages
    ['settings-new-username', 'settings-pass-for-username',
     'settings-new-email',    'settings-pass-for-email',
     'settings-current-password', 'settings-new-password', 'settings-confirm-password'
    ].forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    ['account-msg-username', 'account-msg-email', 'account-msg-password'].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.className = 'account-message'; }
    });
    switchAccountTab('username');
    document.getElementById('account-settings-overlay').style.display = 'flex';
    setTimeout(() => { const el = document.getElementById('settings-new-username'); if (el) el.focus(); }, 100);
}

function closeAccountSettings() {
    document.getElementById('account-settings-overlay').style.display = 'none';
}

function closeAccountSettingsOnOverlay(event) {
    if (event.target === document.getElementById('account-settings-overlay')) closeAccountSettings();
}

function switchAccountTab(tabName) {
    ['username', 'email', 'password'].forEach(name => {
        const btn     = document.getElementById(`tab-${name}`);
        const content = document.getElementById(`tab-content-${name}`);
        if (btn)     btn.classList.toggle('active', name === tabName);
        if (content) content.style.display = name === tabName ? 'flex' : 'none';
    });
    // Focus first input of selected tab
    const firstInput = document.querySelector(`#tab-content-${tabName} .account-input`);
    if (firstInput) setTimeout(() => firstInput.focus(), 50);
}

function showAccountMsg(msgId, text, isSuccess) {
    const el = document.getElementById(msgId);
    if (!el) return;
    el.textContent = text;
    el.className = 'account-message ' + (isSuccess ? 'success' : 'error');
}

async function submitUpdateUsername() {
    const newUsername    = document.getElementById('settings-new-username').value.trim();
    const currentPassword = document.getElementById('settings-pass-for-username').value;

    if (!newUsername)      return showAccountMsg('account-msg-username', i18n.t('acc_username_required'), false);
    if (!currentPassword)  return showAccountMsg('account-msg-username', i18n.t('acc_password_required'), false);

    try {
        const res = await fetch(`/api/users/${CURRENT_USER_ID}/username`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newUsername, currentPassword })
        });
        const text = await res.text();
        if (res.ok) {
            showAccountMsg('account-msg-username', i18n.t('acc_username_updated'), true);
            localStorage.setItem('CURRENT_USERNAME', newUsername);
            document.getElementById('workspace-name').innerText = newUsername;
            document.getElementById('workspace-avatar').innerText = newUsername.charAt(0).toUpperCase();
            document.getElementById('settings-new-username').value = '';
            document.getElementById('settings-pass-for-username').value = '';
        } else {
            const msgs = {
                'Wrong password':       i18n.t('acc_wrong_password'),
                'Username already exists': i18n.t('acc_username_taken'),
                'User not found':       i18n.t('acc_user_not_found')
            };
            showAccountMsg('account-msg-username', msgs[text] || text, false);
        }
    } catch (e) {
        showAccountMsg('account-msg-username', i18n.t('acc_server_error'), false);
    }
}

async function submitUpdateEmail() {
    const newEmail        = document.getElementById('settings-new-email').value.trim();
    const currentPassword = document.getElementById('settings-pass-for-email').value;

    if (!newEmail)         return showAccountMsg('account-msg-email', i18n.t('acc_email_required'), false);
    if (!currentPassword)  return showAccountMsg('account-msg-email', i18n.t('acc_password_required'), false);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(newEmail)) return showAccountMsg('account-msg-email', i18n.t('email_invalid'), false);

    try {
        const res = await fetch(`/api/users/${CURRENT_USER_ID}/email`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ newEmail, currentPassword })
        });
        const text = await res.text();
        if (res.ok) {
            showAccountMsg('account-msg-email', i18n.t('acc_email_updated'), true);
            document.getElementById('settings-new-email').value = '';
            document.getElementById('settings-pass-for-email').value = '';
        } else {
            const msgs = {
                'Wrong password':    i18n.t('acc_wrong_password'),
                'Email already in use': i18n.t('acc_email_taken'),
                'User not found':    i18n.t('acc_user_not_found')
            };
            showAccountMsg('account-msg-email', msgs[text] || text, false);
        }
    } catch (e) {
        showAccountMsg('account-msg-email', i18n.t('acc_server_error'), false);
    }
}

async function submitUpdatePassword() {
    const currentPassword = document.getElementById('settings-current-password').value;
    const newPassword     = document.getElementById('settings-new-password').value;
    const confirmPassword = document.getElementById('settings-confirm-password').value;

    if (!currentPassword)  return showAccountMsg('account-msg-password', i18n.t('acc_password_required'), false);
    if (!newPassword)      return showAccountMsg('account-msg-password', i18n.t('acc_new_password_required'), false);
    if (newPassword.length < 6) return showAccountMsg('account-msg-password', i18n.t('acc_password_too_short'), false);
    if (newPassword !== confirmPassword) return showAccountMsg('account-msg-password', i18n.t('acc_password_mismatch'), false);

    try {
        const res = await fetch(`/api/users/${CURRENT_USER_ID}/password`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword, newPassword })
        });
        const text = await res.text();
        if (res.ok) {
            showAccountMsg('account-msg-password', i18n.t('acc_password_updated'), true);
            document.getElementById('settings-current-password').value = '';
            document.getElementById('settings-new-password').value = '';
            document.getElementById('settings-confirm-password').value = '';
        } else {
            const msgs = {
                'Wrong password': i18n.t('acc_wrong_password'),
                'User not found': i18n.t('acc_user_not_found')
            };
            showAccountMsg('account-msg-password', msgs[text] || text, false);
        }
    } catch (e) {
        showAccountMsg('account-msg-password', i18n.t('acc_server_error'), false);
    }
}

// Close account settings modal on Escape
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        const overlay = document.getElementById('account-settings-overlay');
        if (overlay && overlay.style.display === 'flex') { closeAccountSettings(); return; }
    }
});
