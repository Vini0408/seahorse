require('./file-list.less');

class FileListController {
  /* @ngInject */
  constructor($scope) {

    $scope.$watch(() => this.parents, (parents) => {
      if (parents && parents.length > 0) {
        const lastParent = parents[parents.length - 1];
        this.parent = {
          name: '..',
          uri: lastParent.uri,
          kind: 'parent'
        };
      } else {
        this.parent = null;
      }
    });
  }

}

export default FileListController;
