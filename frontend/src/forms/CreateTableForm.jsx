import { useRef } from "react";

import TextField from '@mui/material/TextField';
import Autocomplete from '@mui/material/Autocomplete';
import { Textarea } from "./TextArea";

export default function CreateTableForm () {
    const table = useRef();
    
    return (
        <>
            <TextField
                required
                id="outlined-required"
                defaultValue="Hello World"
            />
            <TextField
                required
                id="outlined-required"
                type='date'
            />
            <Textarea minRows={3} />
            <Textarea minRows={3} />
            <TextField
                required
                id="outlined-required"
                defaultValue="Hello World"
            />
            <Autocomplete
                multiple
                limitTags={2}
                id="multiple-limit-tags"
                options={["Gore", "Safeword", "D&D"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={["Gore", "Safeword", "D&D"]}
                renderInput={(params) => (
                    <TextField {...params} label="limitTags" placeholder="Favorites" />
                )}
                sx={{ width: '500px' }}
            />
            <Autocomplete
                multiple
                limitTags={2}
                id="multiple-limit-tags"
                options={["Gore", "Safeword", "D&D"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={["Gore", "Safeword", "D&D"]}
                renderInput={(params) => (
                    <TextField {...params} label="limitTags" placeholder="Favorites" />
                )}
                sx={{ width: '500px' }}
            />
            <Autocomplete
                multiple
                limitTags={2}
                id="multiple-limit-tags"
                options={["Gore", "Safeword", "D&D"]}
                // getOptionLabel={(option) => option.title}
                defaultValue={["Gore", "Safeword", "D&D"]}
                renderInput={(params) => (
                    <TextField {...params} label="limitTags" placeholder="Favorites" />
                )}
                sx={{ width: '500px' }}
            />
            
            
        </>
    );
}