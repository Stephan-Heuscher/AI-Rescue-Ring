const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');
const fetch = require('node-fetch');

admin.initializeApp();

// Set global options to use the correct region
setGlobalOptions({ region: 'europe-west1' });

// In-memory rate limiting map
// Key: uid, Value: { count: number, resetAt: number }
const rateLimits = new Map();
const RATE_LIMIT_MAX = 30;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000; // 1 hour

exports.geminiProxy = onCall(async (request) => {
  // 1. Verify Authentication
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError(
      'unauthenticated',
      'User must be authenticated to call this function.'
    );
  }

  const uid = request.auth.uid;
  const data = request.data;

  // 2. Rate Limiting Check
  const now = Date.now();
  let userLimit = rateLimits.get(uid);

  if (!userLimit || userLimit.resetAt < now) {
    userLimit = { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS };
  } else {
    userLimit.count += 1;
  }
  rateLimits.set(uid, userLimit);

  if (userLimit.count > RATE_LIMIT_MAX) {
    throw new HttpsError(
      'resource-exhausted',
      'Rate limit exceeded. Please try again later.'
    );
  }

  // 3. Prepare Request to Gemini API
  // Using environment variables or functions config fallback
  // For Gen 2, it's recommended to use Secret Manager, but here we keep it simple
  const apiKey = process.env.GEMINI_API_KEY;
  
  if (!apiKey) {
    console.error('GEMINI_API_KEY not configured on server.');
    throw new HttpsError(
      'internal',
      'Server configuration error. API key missing.'
    );
  }

  const model = data.model || 'gemini-1.5-flash';
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

  // Construct the payload from client data
  const payload = {
    contents: data.contents,
  };
  
  if (data.systemInstruction) {
      payload.systemInstruction = data.systemInstruction;
  }
  
  if (data.generationConfig) {
      payload.generationConfig = data.generationConfig;
  }

  try {
    // 4. Forward to Gemini API
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    const jsonResponse = await response.json();

    if (!response.ok) {
      console.error('Gemini API Error:', jsonResponse);
      throw new HttpsError(
        'internal',
        `Gemini API returned an error: ${jsonResponse.error?.message || 'Unknown error'}`
      );
    }

    // 5. Return response
    return jsonResponse;
  } catch (error) {
    console.error('Fetch error:', error);
    throw new HttpsError('internal', 'Error communicating with Gemini API.');
  }
});
