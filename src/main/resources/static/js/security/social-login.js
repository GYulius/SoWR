/**
 * Social Login Functions
 * Handles authentication via social media providers (Facebook, Twitter, TikTok)
 * 
 * Dependencies:
 * - getBasePath(): Function to get the base API path
 * - frontendConfig: Global configuration object
 * - facebookAppId: Global variable for Facebook App ID
 */

// Social Login Functions
async function loginWithFacebook() {
    try {
        // Check if Facebook SDK is loaded
        if (typeof FB === 'undefined') {
            alert('Facebook SDK is still loading. Please wait a moment and try again.');
            return;
        }
        
        // Ensure Facebook SDK is initialized
        if (!facebookAppId) {
            const appId = await getFacebookAppId().catch(() => null);
            if (!appId) {
                alert('Facebook login is not configured. Please contact the administrator.');
                return;
            }
            facebookAppId = appId;
            const fbVersion = frontendConfig.facebook?.version || 'v24.0';
            FB.init({
                appId: appId,
                xfbml: true,
                version: fbVersion
            });
        }
        
        // Use Facebook SDK login dialog
        FB.login(function(response) {
            if (response.authResponse) {
                console.log('Welcome! Fetching your information....');
                
                // Get user information from Facebook
                FB.api('/me', {fields: 'name,email'}, async function(userInfo) {
                    try {
                        const basePath = getBasePath();
                        
                        // Send Facebook access token and user info to backend for authentication
                        const loginResponse = await fetch(`${basePath}/auth/facebook/login`, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            credentials: 'include',
                            body: JSON.stringify({
                                accessToken: response.authResponse.accessToken,
                                userId: userInfo.id,
                                name: userInfo.name,
                                email: userInfo.email
                            })
                        });
                        
                        if (loginResponse.ok) {
                            const authData = await loginResponse.json();
                            
                            // Store authentication data
                            if (authData.token) {
                                localStorage.setItem('token', authData.token);
                            }
                            if (authData.userId) {
                                localStorage.setItem('user', JSON.stringify({
                                    userId: authData.userId,
                                    email: authData.email,
                                    firstName: authData.firstName,
                                    lastName: authData.lastName,
                                    role: authData.role
                                }));
                            }
                            
                            // Update UI
                            document.getElementById('loginNavItem').style.display = 'none';
                            document.getElementById('userNavItem').style.display = 'block';
                            document.getElementById('myInterestsNavItem').style.display = 'block';
                            
                            // Close login modal if open
                            const loginModal = bootstrap.Modal.getInstance(document.getElementById('loginModal'));
                            if (loginModal) {
                                loginModal.hide();
                            }
                            
                            // Reload page to show user-specific content
                            window.location.reload();
                        } else {
                            const error = await loginResponse.text();
                            alert('Facebook login failed: ' + error);
                        }
                    } catch (error) {
                        console.error('Error processing Facebook login:', error);
                        alert('Failed to complete Facebook login. Please try again.');
                    }
                });
            } else {
                // User cancelled login or did not fully authorize
                console.log('User cancelled login or did not fully authorize.');
            }
        }, {scope: 'email,public_profile'});
    } catch (error) {
        console.error('Facebook login error:', error);
        alert('Failed to initiate Facebook login. Please try again.');
    }
}

// Helper function to get Facebook App ID from backend or config
async function getFacebookAppId() {
    // First check if we have it in the loaded config
    if (frontendConfig.facebook && frontendConfig.facebook.appId) {
        return frontendConfig.facebook.appId;
    }
    
    // Fallback to the old endpoint for backward compatibility
    const basePath = getBasePath();
    try {
        const response = await fetch(`${basePath}/auth/facebook/config`, {
            method: 'GET',
            credentials: 'include'
        });
        
        if (response.ok) {
            const config = await response.json();
            // Update frontend config cache
            if (config.appId) {
                frontendConfig.facebook.appId = config.appId;
            }
            return config.appId;
        }
    } catch (error) {
        console.warn('Could not fetch Facebook config from backend:', error);
    }
    
    // Fallback: return null to indicate configuration is needed
    return null;
}

function loginWithTwitter() {
    alert('Twitter login will be implemented. For now, please use email/password login.');
    // TODO: Implement Twitter OAuth
    // window.location.href = '/auth/twitter';
}

function loginWithTikTok() {
    alert('TikTok login will be implemented. For now, please use email/password login.');
    // TODO: Implement TikTok OAuth
    // window.location.href = '/auth/tiktok';
}

