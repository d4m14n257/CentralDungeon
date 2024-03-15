import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';

export default function ViewMoreComponent ({ handleTableSelect, value }) {
    return (
        <Typography 
            sx={{textAlign: 'center'}}
        >
            <Button startIcon={<KeyboardArrowDownIcon />} onClick={() => {value ? handleTableSelect(value) : handleTableSelect()}}>
                Ver mas
            </Button>
        </Typography>
    );
}