import { writable } from 'svelte/store';

/**
 * Mirror of SpringKraftAdminProperties
 * This serves as the single source of truth for the UI.
 */
export const adminSettings = writable({
    version: '0.0.1',
    basePath: '/admin',
    title: 'KraftAdmin',
    logoUrl: null as string | null,
    
    theme: {
        primaryColor: '#3b82f6',
        darkMode: true
    },
    
    storage: {
        uploadDir: 'uploads/admin',
        publicUrlPrefix: '/admin/files'
    },
    
    security: {
        cookieName: 'KRAFT_SESSION',
        sessionExpiryMinutes: 60,
        requiredRoles: ['ROLE_ADMIN'],
        protectedRoutes: {} as Record<string, string[]>
    },
    
    pagination: {
        defaultPageSize: 20,
        maxPageSize: 100
    },
    
    features: {
        allowDelete: true,
        showTimestamps: true,
        readOnly: false
    },
    
    localeConfig: {
        defaultLanguage: 'en',
        timezone: 'UTC'
    },
    
    telemetryConfig: {
        cloudUrl: 'http://localhost:8090',
        enabled: true
    }
});