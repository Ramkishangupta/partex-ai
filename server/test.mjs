import { createClient } from "@deepgram/sdk";
const client = createClient("1ae1f8c5c0220c59987ab34e54a2e0d3703cc5f9");
const connection = client.listen.live({ model: "nova-2", language: "hi" });
connection.on("open", () => { console.log("OPEN"); connection.finish(); });
connection.on("error", (e) => console.log("ERROR", e));
connection.on("close", () => console.log("CLOSE"));
