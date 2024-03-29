import express, { Express } from "express";
import dotenv from 'dotenv';
import { systems } from "./router/systems";
import { tags } from "./router/tags";
import { platform } from "os";
import { tables } from "./router/tables";

//TODO: Change every status 418 because it isnt a teatpot
//TODO: Keep only the number and simbol en utc.

dotenv.config();

const app : Express = express();
const port = process.env.PORT;

app.use(express.json());
app.use('/systems', systems);
app.use('/tags', tags);
app.use('/platform', platform);
app.use('/tables', tables);

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`)
});