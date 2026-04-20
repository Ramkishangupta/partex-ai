import OpenAI from "openai";
import dotenv from "dotenv";

dotenv.config({ path: '../.env' });

let grokClient = null;

export const getGrokClient = () => {
  if (!grokClient) {
    const apiKey = process.env.GROK_API_KEY;
    if (!apiKey) {
      console.warn('⚠️  GROK_API_KEY not configured. LLM extraction will not work.');
      return null;
    }

    grokClient = new OpenAI({
      apiKey: apiKey,
      baseURL: "https://api.xai.com/v1",
    });
    console.log(`✅ Grok client initialized`);
  }
  return grokClient;
};
