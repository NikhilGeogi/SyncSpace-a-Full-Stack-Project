const translations = {
    // Login Page
    login_title: "Login",
    login_subtitle: "Welcome back to your workspace",
    email_label: "Email",
    email_placeholder: "Enter your email",
    password_label: "Password",
    password_placeholder: "Enter your password",
    continue_btn: "Continue",
    no_account: "Don't have an account?",
    signup_link: "Sign up here",
    login_error_empty: "Invalid email or password. Please try again.",
    login_error_server: "Server connection failed, please check if the backend is running.",

    // Register Page
    register_title: "Create an account",
    username_label: "Username",
    username_placeholder: "Enter your username",
    register_btn: "Sign Up",
    has_account: "Already have an account?",
    login_link: "Log in here",
    register_success: "Registration successful! Redirecting to login...",

    // Workspace Page
    workspace_loading: "Loading...",
    workspace_title: "Workspace ({0})",
    logout: "Log Out",
    new_page_btn: "+ New Page",
    search_placeholder: "Search pages and content",
    search_btn: "Search",
    search_empty: "No matching pages found.",
    trash_btn: "Trash",
    trash_title: "Trash",
    trash_empty: "Trash is empty.",
    restore_btn: "Restore",
    permanently_delete_btn: "Delete permanently",
    delete_page_confirm: "Move this page and its sub-pages to trash?",
    permanent_delete_confirm: "Permanently delete this page and its sub-pages? This cannot be undone.",
    share_page_title: "Share page",
    share_email_placeholder: "Existing user's email",
    permission_viewer: "Viewer",
    permission_editor: "Editor",
    share_btn: "Share",
    remove_share_btn: "Remove",
    share_failed: "Could not update page sharing.",
    default_header: "Please select or create a page",
    create_new_page: "Create New Page",
    create_sub_page: "Create Sub-page (ID: {0})",
    create_top_page: "Create New Page (Top Level)",
    untitled_placeholder: "Untitled",
    cancel_btn: "Cancel",
    confirm_btn: "Confirm",
    save_btn: "Save",
    workspace_breadcrumb: "Workspace",

    // Context Menu
    turn_into: "Turn into",
    text: "Text",
    heading_1: "Heading 1",
    to_do: "To-do",
    page: "Page",
    duplicate: "Duplicate",
    delete: "Delete",

    // Script dynamic messages
    delete_page_failed: "Failed to delete page",
    create_page_failed: "Failed to create page",
    untitled_page: "Untitled Page",
    deleted_page: "This page has been deleted",
    title_required: "Please enter a title!",
    server_error: "An error occurred, please check if the backend is running.",
    email_invalid: "Please enter a valid email address (e.g. user@example.com).",

    // Account Settings UI
    account_settings_title: "⚙️ Account Settings",
    tab_username: "Username",
    tab_email: "Email",
    tab_password: "Password",
    acc_new_username_label: "New Username",
    acc_new_username_placeholder: "Enter new username",
    acc_current_password_label: "Current Password",
    acc_verify_password_placeholder: "Verify your password",
    acc_new_email_label: "New Email",
    acc_new_email_placeholder: "Enter new email",
    acc_current_password_placeholder: "Current password",
    acc_new_password_label: "New Password",
    acc_new_password_placeholder: "New password",
    acc_confirm_password_label: "Confirm New Password",
    acc_confirm_password_placeholder: "Confirm new password",

    // Account Settings messages
    acc_username_required: "Please enter a new username.",
    acc_password_required: "Please enter your current password.",
    acc_email_required: "Please enter a new email address.",
    acc_username_updated: "✓ Username updated successfully.",
    acc_email_updated: "✓ Email updated successfully.",
    acc_password_updated: "✓ Password updated successfully.",
    acc_wrong_password: "Incorrect password, please try again.",
    acc_username_taken: "This username is already taken.",
    acc_email_taken: "This email is already in use.",
    acc_user_not_found: "User not found.",
    acc_server_error: "Server error, please try again later.",
    acc_new_password_required: "Please enter a new password.",
    acc_password_too_short: "New password must be at least 6 characters.",
    acc_password_mismatch: "The two passwords do not match."
};

const i18n = {
    init() {
        this.applyLanguage();
    },

    t(key, ...args) {
        let text = translations[key] || key;
        args.forEach((arg, i) => {
            text = text.replace(`{${i}}`, arg);
        });
        return text;
    },

    applyLanguage() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (translations[key]) {
                if (el.tagName === 'INPUT' && el.hasAttribute('placeholder')) {
                    el.setAttribute('placeholder', this.t(key));
                } else {
                    el.innerText = this.t(key);
                }
            }
        });
    }
};

document.addEventListener('DOMContentLoaded', () => {
    i18n.init();
});
