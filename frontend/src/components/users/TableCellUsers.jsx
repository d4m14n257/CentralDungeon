import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import ThumbUpAltIcon from '@mui/icons-material/ThumbUpAlt';
import ThumbDownAltIcon from '@mui/icons-material/ThumbDownAlt';
import DragHandleIcon from '@mui/icons-material/DragHandle';

export default function TableCellUsers (props) {
    const { index, row } = props;

    if(index === 'joined_table') {
        return (
            <ButtonGroup variant="outlined" aria-label="outlined button group">
              <Button>{row.inscrited_table}</Button>
              <Button>{row.active_table}</Button>
            </ButtonGroup>
        );
    }
    else if(index === 'master_table') {
        return(
            <ButtonGroup variant="outlined" aria-label="outlined button group">
              <Button>{row.active_master_table}</Button>
              <Button>{row.created_master_table}</Button>
            </ButtonGroup>
        );
    }
    else if(index === 'comments') {
        return(
            <ButtonGroup variant="outlined" aria-label="outlined button group">
              <Button endIcon={<ThumbUpAltIcon/>}>{row.positive_comments}</Button>
              <Button endIcon={<DragHandleIcon />}>{row.neutral_comments}</Button>
              <Button endIcon={<ThumbDownAltIcon/>}>{row.negative_comments}</Button>
            </ButtonGroup>
        );
    }

}